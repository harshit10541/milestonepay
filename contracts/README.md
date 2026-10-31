# Backend ↔ AI service contract

The two JSON calls between the backend (`backend/`) and the AI service (`ai-service/`).
**Never change these files without both Harshit and Srushti agreeing.** A change here is a contract change: tell the other person first, then update the schema, the examples, `MockAiClient` and the AI service together.

The AI only checks and reports. Nothing in these responses moves money: the backend releases only on client approval, review-window timeout or a mediator, and never auto-passes `fail` or `unsure`.

## Files

| Call | Direction | Schema | Example |
|---|---|---|---|
| 1. Generate criteria, request | backend → AI | [`schemas/criteria-request.schema.json`](schemas/criteria-request.schema.json) | [`examples/criteria-request.example.json`](examples/criteria-request.example.json) |
| 1. Generate criteria, response | AI → backend | [`schemas/criteria-response.schema.json`](schemas/criteria-response.schema.json) | [`examples/criteria-response.example.json`](examples/criteria-response.example.json) |
| 2. Review submission, request | backend → AI | [`schemas/review-request.schema.json`](schemas/review-request.schema.json) | [`examples/review-request.example.json`](examples/review-request.example.json) |
| 2. Review submission, response | AI → backend | [`schemas/review-response.schema.json`](schemas/review-response.schema.json) | [`examples/review-response.pass.example.json`](examples/review-response.pass.example.json) (good site), [`examples/review-response.broken-form.example.json`](examples/review-response.broken-form.example.json) (the demo's broken contact form) |

Schemas are JSON Schema draft 2020-12 and self-contained (the criterion definition is repeated in call 1's response and call 2's request; keep the two copies identical, a backend test checks this). The backend tests check every example against its schema and check that `MockAiClient` only uses values listed here.

## The shape (agreed, unchanged)

```
Call 1 request   { jobId, milestoneId, brief }
Call 1 response  { criteria: [{ id, text, type: "objective"|"subjective", checkMethod }] }
Call 2 request   { milestoneId, submissionUrl, criteria }            // criteria as returned by call 1
Call 2 response  { results: [{ criterionId, status: "pass"|"fail"|"unsure", confidence, evidence, screenshotUrl }], summary }
```

Limits the backend enforces (from `CriteriaService` and `CreateJobRequest`), so the AI service must stay inside them:

- `brief`: 1 to 10,000 characters. `jobId`, `milestoneId`: UUID strings.
- 1 to 30 criteria. `id`: required, unique in the response, at most 64 characters (`c1`, `c2`, …). The backend stores it, and call 2's `criterionId` must use the same ids.
- `text`: required, at most 1,000 characters. `type`: exactly `objective` or `subjective`.
- `confidence`: a number from 0 to 1. Anything under the confidence threshold is sent as `unsure`; the AI service applies the threshold, the backend doesn't re-check it.
- `screenshotUrl`: an http(s) URL hosted by the AI service, or `null` when there is nothing to show.
- Length caps on `evidence` (2,000), `summary` (4,000) and URLs (2,048) are new in the schema, picked as generous safety limits. Say if any is too tight.

## `checkMethod` values

> **PROPOSAL, Srushti to confirm.** This fills the one gap in the agreed contract: the build roadmap used `browser_timing`, `browser_form`, `client_review`, and the old mock used `http_status`, `form_submit`, `viewport_check`, `load_time`, `link_check`, `client_judgement`. This list is the roadmap's suggested starting set, in the roadmap's naming style. Change it here (and in `MockAiClient`) if you want different names.

| Value | `type` | What the review agent checks |
|---|---|---|
| `browser_timing` | objective | The page loads: HTTP status and load time (the limit is in the criterion text, e.g. "under 3 seconds"). |
| `browser_element` | objective | An element or section exists on the page (e.g. a pricing section, a nav link). |
| `browser_form` | objective | Fill the form with test data, submit, and look for a success message or an error response. The demo's key check. |
| `browser_mobile` | objective | At a 375px-wide viewport the page has no horizontal scroll. |
| `client_review` | subjective | No automatic check. The client judges it. |

Rules (enforced by the schema):

- `checkMethod` is always sent.
- Every `subjective` criterion has `client_review`, and no `objective` criterion does.
- If the criteria agent can't map a requirement to one of the browser checks, it makes the criterion `subjective` rather than inventing a check the review agent can't run.

Adding a check method is a contract change: add it here, in the schema enum, in `com.milestonepay.ai.CheckMethod`, and in the review agent.

## Service auth

Every request from the backend to the AI service carries

```
Authorization: Bearer <AI_SERVICE_SECRET>
```

on every endpoint except `GET /health`. The secret lives in the `AI_SERVICE_SECRET` env var on both sides (see `backend/.env.example`), never in the repo. The AI service compares it in constant time (e.g. `hmac.compare_digest` in Python) and answers `401` when it is missing or wrong. This is the recommendation on file in the AI roadmap; the backend will send it once `HttpAiClient` exists.

## Still open (not decided by this folder)

These are on the team's open list. Record the answer here when you agree it:

- **Endpoint paths** for call 1 and call 2 (e.g. `POST /criteria`, `POST /review`). Needed before `HttpAiClient`.
- **Subjective items in call 2 results**: leave them out, or return them as `unsure` with evidence like "For the client to judge". The schema allows both today (results may cover fewer criteria than were sent), and the examples leave them out.
- **Confidence threshold** for `unsure` (e.g. start at 0.7 and tune it with the test set).
- **Error convention**: proposed in the AI roadmap that an unreachable submitted site still gets HTTP 200 with every result `unsure`, and 5xx is reserved for the AI service itself failing, so the backend worker can retry 5xx safely.
- **Timeouts** for each call.
