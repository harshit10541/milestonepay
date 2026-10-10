# Session 2: detailed steps (Harshit, backend)

Today's goal: the repo skeleton is merged, the repo is connected to this project, and a sandbox payment is **authorized → captured** (and another is **voided**) from your Spring Boot code. That's your half of the Week 1 gate.

Work through Parts A → D in order. Each step ends with a **✅ Done when** check, so you can tell when to move on.

---

## Part A: Finish the repo setup (≈45 min)

### A1. Protect `main`
1. github.com → your `milestonepay` repo → **Settings → Rules → Rulesets → New ruleset → New branch ruleset**.
2. Name: `main-protection`. Enforcement status: **Active**.
3. Target branches → **Add target → Include default branch**.
4. Tick:
   - **Restrict deletions**
   - **Require a pull request before merging**, with required approvals = **1**
   - **Block force pushes**
5. Save.

⚠️ GitHub only *enforces* rulesets on **private** repos on a paid plan (Pro/Team). If you're on the free plan and the repo is private, the ruleset saves but shows as not enforced. Then either make the repo public for the hackathon or just follow the rule by hand. Check what GitHub shows on the ruleset page.

✅ Done when: the ruleset shows as Active on `main`.

### A2. Agree on the layout and branch names with Srushti (5 min message)
Send her this (a recommendation, not decided yet):
> Monorepo: `backend/`, `ai-service/`, `frontend/`, `contracts/`, `docs/`, root `CLAUDE.md`. Branches: `backend/<thing>`, `ai/<thing>`, `shared/<thing>`. DB: PostgreSQL + Spring Data JPA + Flyway. OK?

The DB choice doesn't touch the AI contract, but it's listed as undecided, so get her yes and then record it in `context/decisions.md`.

✅ Done when: she has said yes (or suggested changes).

### A3. Generate the Spring Boot skeleton
1. Clone the repo and create a branch:
   ```bash
   git clone git@github.com:harshit10541/milestonepay.git
   cd milestonepay
   git checkout -b backend/skeleton
   ```
2. Go to https://start.spring.io and choose:
   - Project: **Maven** · Language: **Java** · Spring Boot: latest **3.x** stable (not a SNAPSHOT/M version)
   - Group: `com.milestonepay` · Artifact: `backend` · Name: `backend` · Package name: `com.milestonepay`
   - Packaging: **Jar** · Java: **21**
   - Dependencies: **Spring Web, Validation, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Spring Boot Actuator**
3. **Generate**, then unzip so the files land directly in `milestonepay/backend/` (you should see `backend/pom.xml` and `backend/mvnw`).
4. Create the module packages (run from the repo root):
   ```bash
   cd backend/src/main/java/com/milestonepay
   for p in payments jobs criteria submissions release webhooks workers events common; do
     mkdir -p $p && echo "package com.milestonepay.$p;" > $p/package-info.java
   done
   cd -   # back to repo root
   ```
5. Add the root `.gitignore` from `backend/repo-setup.md` Step 3.5 (the `.env`, `backend/target/`, IDE and Python entries).
6. Replace `backend/src/main/resources/application.properties` with `application.yml`:
   ```yaml
   spring:
     application:
       name: milestonepay-backend
     datasource:
       url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/postgres}
       username: ${DB_USER:postgres}
       password: ${DB_PASSWORD:dev}
     jpa:
       hibernate:
         ddl-auto: validate   # Flyway owns the schema, Hibernate only checks it
       open-in-view: false
   paypal:
     base-url: ${PAYPAL_BASE_URL:https://api-m.sandbox.paypal.com}
     client-id: ${PAYPAL_CLIENT_ID:}
     client-secret: ${PAYPAL_CLIENT_SECRET:}
   management:
     endpoints:
       web:
         exposure:
           include: health
   ```
7. Flyway needs at least one migration folder. Create `backend/src/main/resources/db/migration/V1__init.sql` with just a comment for now:
   ```sql
   -- Schema comes in the Week 1 schema task (see week1-start-paypal.md Step 4).
   ```
8. Start Postgres and run the app:
   ```bash
   docker run -d --name mp-db -p 5432:5432 -e POSTGRES_PASSWORD=dev postgres:16
   cd backend
   ./mvnw spring-boot:run
   ```
   In another terminal: `curl localhost:8080/actuator/health` → `{"status":"UP"}`.

   If it fails: "Connection refused" means Postgres isn't up (`docker ps`); "port 5432 already in use" means a local Postgres is already running (stop it or use `-p 5433:5432` and change `DATABASE_URL`).

✅ Done when: health returns `UP`.

### A4. Add `CLAUDE.md` and `.env.example`
1. Copy the starter `CLAUDE.md` from `backend/repo-setup.md` Step 4 to the repo root.
2. Create `backend/.env.example` with the variable names from `backend/repo-setup.md` Step 6 (names only, no real values).
3. Create your real `backend/.env` (gitignored) and fill it in during Part C.

To load `.env` when running locally, the simplest way is:
```bash
cd backend
set -a; source .env; set +a
./mvnw spring-boot:run
```
(Or use IntelliJ's run configuration → Environment variables, or the EnvFile plugin.)

### A5. Open the PR
```bash
git add .
git status            # make sure no .env is listed
git commit -m "Backend skeleton: Spring Boot 3, module packages, config, CLAUDE.md"
git push -u origin backend/skeleton
```
On GitHub, open the PR into `main` and request Srushti as the reviewer.

✅ Done when: the PR is open (merging can happen while you do Part C; continue on a branch off it if she hasn't reviewed yet).

---

## Part B: Connect the repo to this project (≈5 min)

1. Open https://claude.ai/connect-github and connect your GitHub account.
2. On the same flow, install the **Claude GitHub App** and give it access to the `milestonepay` repo (choose "Only select repositories").
3. In this project, open [Project settings → Resources](#project-settings/resources) and add `milestonepay`.

✅ Done when: the repo appears under Resources. Tell me, and I can read the code, review your PR, and write code on branches.

---

## Part C: PayPal sandbox setup (≈30 min)

1. Go to https://developer.paypal.com and log in (a normal PayPal account works; it creates a developer account).
2. Top right, make sure the toggle is on **Sandbox**.
3. **Apps & Credentials → Create App**:
   - App name: `milestonepay-dev` · Type: **Merchant**
   - Copy **Client ID** and **Secret** into `backend/.env`:
     ```
     PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
     PAYPAL_CLIENT_ID=...
     PAYPAL_CLIENT_SECRET=...
     ```
4. **Testing Tools → Sandbox Accounts**. You should see two default accounts:
   - **Business** (`...-facilitator@...`): the freelancer / payee. The app you created is linked to this account.
   - **Personal** (`...-buyer@...`): the client / payer.
   Click **⋯ → View/Edit account** on the Personal one and note its email and password (you'll log in with it to approve payments). If it has no balance, set one there.
5. Check the credentials:
   ```bash
   set -a; source backend/.env; set +a
   curl -s -u "$PAYPAL_CLIENT_ID:$PAYPAL_CLIENT_SECRET" \
     -d grant_type=client_credentials \
     https://api-m.sandbox.paypal.com/v1/oauth2/token
   ```
   You should get JSON with `access_token` and `expires_in`. A `401 invalid_client` means a wrong ID/secret, or you copied Live credentials instead of Sandbox.
6. Share the sandbox credentials with Srushti privately (not in the repo).

✅ Done when: you get an `access_token`.

---

## Part D: Authorize → capture / void from code (main work, ≈2–3 hours)

Branch: `git checkout -b backend/paypal-auth-capture` (off `backend/skeleton` if it isn't merged yet).

⚠️ Request/response fields below are from memory. Keep the PayPal Orders v2 and Payments v2 API reference open (developer.paypal.com → API Reference) and check each call against it.

### D1. Config properties
`common/PayPalProperties.java` (or keep it in `payments`):
```java
package com.milestonepay.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paypal")
public record PayPalProperties(String baseUrl, String clientId, String clientSecret) {}
```
Turn it on in `BackendApplication.java`:
```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication { ... }
```

### D2. Money helper
`common/Money.java`:
```java
package com.milestonepay.common;

import java.math.BigDecimal;
import java.util.Currency;

public final class Money {
    private Money() {}

    /** 30000 + "USD" → "300.00". Only used at the PayPal boundary. */
    public static String toPayPalString(long minor, String currency) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        return BigDecimal.valueOf(minor, digits).toPlainString();
    }
}
```

### D3. `PayPalClient` (package-private, inside `payments`)
Use the class from `week1-start-paypal.md` Step 3. Add the imports (`org.springframework.web.client.RestClient`, `org.springframework.http.MediaType`, `org.springframework.util.LinkedMultiValueMap`, `com.fasterxml.jackson.databind.JsonNode`, `java.time.Instant`, `java.util.Map`).

Add one helper so you can see PayPal's error body when something fails (you will hit errors on the first tries):
```java
.retrieve()
.onStatus(HttpStatusCode::isError, (req, res) -> {
    String err = new String(res.getBody().readAllBytes());
    throw new PayPalException(res.getStatusCode().value(), err);
})
.body(JsonNode.class);
```
```java
package com.milestonepay.payments;

public class PayPalException extends RuntimeException {
    private final int status;
    public PayPalException(int status, String body) {
        super("PayPal " + status + ": " + body);
        this.status = status;
    }
    public int status() { return status; }
}
```

### D4. `PaymentService` (the only public entry point)
Use the class from `week1-start-paypal.md` Step 3, plus the two missing pieces:
```java
public record CreatedOrder(String orderId, String approveUrl) {}

private static String findLink(JsonNode res, String... rels) {
    for (String rel : rels) {
        for (JsonNode link : res.path("links")) {
            if (rel.equals(link.path("rel").asText())) return link.path("href").asText();
        }
    }
    throw new IllegalStateException("No approval link in PayPal response: " + res);
}
```
Add a status check you'll use while testing:
```java
public JsonNode getAuthorization(String authorizationId) {
    return paypal.get("/v2/payments/authorizations/" + authorizationId);
}
```
(and a matching `get(path)` method in `PayPalClient`: same as `post`, without body or request id).

Idempotency keys: `order-<milestoneId>`, `authorize-<milestoneId>`, `capture-<milestoneId>`, `void-<milestoneId>`. Same key means PayPal returns the first result instead of acting twice.

### D5. Dev-only controller (for testing by hand)
`payments/DevPaymentController.java`, active only with the `dev` profile so it never ships:
```java
package com.milestonepay.payments;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@Profile("dev")
@RestController
@RequestMapping("/dev/payments")
class DevPaymentController {
    private final PaymentService payments;
    DevPaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping("/orders")
    PaymentService.CreatedOrder create(@RequestParam UUID milestoneId,
                                       @RequestParam long amountMinor,
                                       @RequestParam(defaultValue = "USD") String currency) {
        return payments.createOrder(milestoneId, amountMinor, currency);
    }

    @PostMapping("/orders/{orderId}/authorize")
    Map<String, String> authorize(@PathVariable String orderId, @RequestParam UUID milestoneId) {
        return Map.of("authorizationId", payments.authorize(milestoneId, orderId));
    }

    @PostMapping("/authorizations/{authId}/capture")
    void capture(@PathVariable String authId, @RequestParam UUID milestoneId) {
        payments.capture(milestoneId, authId);
    }

    @PostMapping("/authorizations/{authId}/void")
    void voidHold(@PathVariable String authId, @RequestParam UUID milestoneId) {
        payments.voidHold(milestoneId, authId);
    }

    @GetMapping("/authorizations/{authId}")
    Object status(@PathVariable String authId) {
        return payments.getAuthorization(authId);
    }

    // PayPal redirects the buyer here after approval; just show the token (= order id).
    @GetMapping("/return")
    Map<String, String> paypalReturn(@RequestParam Map<String, String> params) { return params; }
}
```
Point the `return_url` / `cancel_url` in `createOrder` at `http://localhost:8080/dev/payments/return`.

Run with the dev profile:
```bash
cd backend
set -a; source .env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### D6. The gate test (do it in this order, write down each ID)
Use a fixed test milestone id, e.g. `M=11111111-1111-1111-1111-111111111111`.

1. **Create order** (300.00 USD = 30000 minor units):
   ```bash
   curl -s -X POST "localhost:8080/dev/payments/orders?milestoneId=$M&amountMinor=30000"
   ```
   → `orderId` + `approveUrl`.
2. **Approve**: open `approveUrl` in a private browser window, log in as the sandbox **Personal** account, approve. You land on `/dev/payments/return?token=<orderId>...`.
3. **Authorize**:
   ```bash
   curl -s -X POST "localhost:8080/dev/payments/orders/<orderId>/authorize?milestoneId=$M"
   ```
   → `authorizationId`. Money is now **held**.
4. **Check status**: `curl -s localhost:8080/dev/payments/authorizations/<authId>` → `"status": "CREATED"`. Note `expiration_time`: that's the hold expiry you need for the open question in `context/open-questions-risks.md`.
5. **Capture**:
   ```bash
   curl -s -X POST "localhost:8080/dev/payments/authorizations/<authId>/capture?milestoneId=$M"
   ```
   Status check → `CAPTURED`. Log in at https://www.sandbox.paypal.com as the **Business** account: the payment shows as received. **Gate passed.** 🎉
6. **Idempotency**: run the same capture command again. It must not charge twice (you should get the same result back, or an "already captured" style error, but never a second capture). Write down which one you get.
7. **Void path**: repeat steps 1–4 with a **new** milestone id (e.g. `...-2222`), then:
   ```bash
   curl -s -X POST "localhost:8080/dev/payments/authorizations/<authId>/void?milestoneId=$M2"
   ```
   Status → `VOIDED`.

Common errors:
- `422 ORDER_NOT_APPROVED` on authorize: you skipped step 2 or approved with the wrong account.
- `422 ... DUPLICATE_INVOICE_ID` or reused-key confusion: use a new milestone id for each new test run, since the keys are derived from it.
- `401`: token problem; check `.env` is loaded in the same terminal that runs the app.

✅ Done when: steps 5, 6 and 7 behave as above. Commit, push, open the PR for Srushti to review.

---

## Write these down for the schema task (Step 4 of the Week 1 guide)
While testing, save one real response of each kind (create order, authorize, capture, void, status) into `docs/paypal-samples/` with IDs redacted if you like. They tell you exactly which columns the `payments` table needs (`paypal_order_id`, `paypal_authorization_id`, status values, `expiration_time`).

## Still open (don't assume)
- Database choice: waiting on Srushti's yes (A2).
- Auth method: undecided; fake the user with a header for now.
- Authorization honor period and expiry: read it off step D6.4 and confirm in the PayPal docs.
