# MilestonePay

PayPal-backed, AI-verified milestone payments for direct freelance work (hackathon project).

## Layout
- backend/: Java 21, Spring Boot 3, Maven. Modular monolith; one package per module under com.milestonepay (payments, jobs, criteria, submissions, release, webhooks, workers, events, common).
- ai-service/: AI criteria and review agents (owned by Srushti). Not created yet.
- contracts/: backend <-> AI JSON contracts. Never change without both people agreeing.

## Rules
- AI only reports. Money moves only on client approval, review-window timeout, or mediator. Never let the AI release payments.
- Money is integer minor units (long) plus a currency code. Never double. Convert to PayPal's decimal string only at the PayPal boundary.
- Only the payments package calls PayPal. Every money call sends a PayPal-Request-Id (e.g. capture-<milestoneId>).
- Modules talk through service methods, never each other's repositories or tables.
- All milestone state changes go through the state machine. State names: draft, criteria_approved, funded, submitted, in_review, awaiting_client, changes_requested, released, cancelled, disputed.
- The events table is append-only: no UPDATE or DELETE.
- Validate every input; check ownership on every endpoint.
- Secrets only in env vars (see backend/.env.example). Never commit .env.
- Branches: backend/<thing>, ai/<thing>, shared/<thing>. The other person reviews every PR.

## Commands
- Run: cd backend && ./mvnw spring-boot:run (needs Postgres, see below)
- Test: cd backend && ./mvnw test
- Local Postgres: docker run -d --name mp-db -p 5432:5432 -e POSTGRES_PASSWORD=dev postgres:16
- Load env: cd backend && set -a; source .env; set +a

## Undecided (do not assume)
Auth method, worker/scheduling approach, AI service language, frontend framework. DB choice (Postgres + JPA + Flyway) is a recommendation awaiting confirmation.
