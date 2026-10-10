-- Week 1 core schema: users, jobs, milestones, criteria, payments, events.
-- Conventions (CLAUDE.md): money in integer minor units + ISO currency, UTC timestamps,
-- milestone state names exactly as agreed, events append-only.

-- Who is calling. Auth method is still undecided, so this holds only what every option needs.
CREATE TABLE users (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email        text        NOT NULL UNIQUE,
    display_name text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- A client hires a freelancer for a job. The freelancer may join after the job is created.
CREATE TABLE jobs (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id     uuid        NOT NULL REFERENCES users (id),
    freelancer_id uuid                 REFERENCES users (id),
    title         text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT jobs_client_is_not_freelancer CHECK (freelancer_id IS NULL OR freelancer_id <> client_id)
);
CREATE INDEX jobs_client_idx ON jobs (client_id);
CREATE INDEX jobs_freelancer_idx ON jobs (freelancer_id);

-- One unit of work with its own amount, criteria, payment and review.
CREATE TABLE milestones (
    id                              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id                          uuid        NOT NULL REFERENCES jobs (id),
    position                        int         NOT NULL CHECK (position >= 1),
    title                           text        NOT NULL,
    brief                           text        NOT NULL,
    amount_minor                    bigint      NOT NULL CHECK (amount_minor > 0),
    currency                        char(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    state                           text        NOT NULL DEFAULT 'draft' CHECK (state IN (
                                        'draft', 'criteria_approved', 'funded', 'submitted', 'in_review',
                                        'awaiting_client', 'changes_requested', 'released', 'cancelled', 'disputed')),
    -- Defaults for these two are still open, so the app must set them per milestone.
    revision_rounds_allowed         int         NOT NULL CHECK (revision_rounds_allowed >= 0),
    revision_rounds_used            int         NOT NULL DEFAULT 0 CHECK (revision_rounds_used >= 0),
    review_window_seconds           int         NOT NULL CHECK (review_window_seconds > 0),
    client_criteria_approved_at     timestamptz,
    freelancer_criteria_approved_at timestamptz,
    review_deadline_at              timestamptz,
    needs_client_decision           boolean     NOT NULL DEFAULT false,
    version                         bigint      NOT NULL DEFAULT 0,   -- JPA @Version (optimistic lock)
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT milestones_job_position_unique UNIQUE (job_id, position)
);
-- The review-window timer looks for milestones whose deadline has passed.
CREATE INDEX milestones_review_deadline_idx ON milestones (review_deadline_at)
    WHERE state = 'awaiting_client';

-- The agreed checklist. ai_id keeps the AI's own id (e.g. "c1") from contract call 1.
CREATE TABLE criteria (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id uuid        NOT NULL REFERENCES milestones (id),
    position     int         NOT NULL CHECK (position >= 1),
    ai_id        text,
    text         text        NOT NULL,
    type         text        NOT NULL CHECK (type IN ('objective', 'subjective')),
    check_method text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT criteria_milestone_position_unique UNIQUE (milestone_id, position)
);

-- PayPal ids for each funding attempt. A new attempt (after a void) gets attempt + 1,
-- which also goes into its PayPal-Request-Id so PayPal does not replay the old order.
CREATE TABLE payments (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id             uuid        NOT NULL REFERENCES milestones (id),
    attempt                  int         NOT NULL DEFAULT 1 CHECK (attempt >= 1),
    status                   text        NOT NULL CHECK (status IN (
                                 'ORDER_CREATED', 'AUTHORIZED', 'CAPTURED', 'VOIDED', 'EXPIRED', 'FAILED')),
    amount_minor             bigint      NOT NULL CHECK (amount_minor > 0),
    currency                 char(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    paypal_order_id          text        NOT NULL UNIQUE,
    paypal_authorization_id  text        UNIQUE,
    paypal_capture_id        text        UNIQUE,
    authorization_expires_at timestamptz,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT payments_milestone_attempt_unique UNIQUE (milestone_id, attempt)
);

-- Append-only audit trail of everything that happened. milestone_id is null for
-- events not tied to one milestone.
CREATE TABLE events (
    id           bigserial PRIMARY KEY,
    milestone_id uuid                 REFERENCES milestones (id),
    type         text        NOT NULL,
    actor        text        NOT NULL,
    data         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX events_milestone_idx ON events (milestone_id, at);

-- Enforce append-only in the database itself, so no code path can edit history.
CREATE FUNCTION events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'events is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER events_no_update_or_delete
    BEFORE UPDATE OR DELETE ON events
    FOR EACH ROW EXECUTE FUNCTION events_append_only();

CREATE TRIGGER events_no_truncate
    BEFORE TRUNCATE ON events
    FOR EACH STATEMENT EXECUTE FUNCTION events_append_only();
