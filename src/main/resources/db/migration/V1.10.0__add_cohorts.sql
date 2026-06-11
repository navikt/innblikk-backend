CREATE TABLE cohorts
(
    id          BIGSERIAL PRIMARY KEY,
    website_id  UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    deleted_at  TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cohorts_name_website UNIQUE (name, website_id)
);

CREATE TABLE cohort_entries
(
    id                   BIGSERIAL PRIMARY KEY,
    cohort_id            BIGINT      NOT NULL,
    negated              BOOLEAN     NOT NULL DEFAULT FALSE,
    in_cohort            BOOLEAN     NOT NULL DEFAULT FALSE,
    operator             VARCHAR(32),
    referenced_cohort_id BIGINT,
    ordering             INT         NOT NULL DEFAULT 0,
    -- Time filtering (not yet implemented in UI/SQL generation)
    -- Relative window: "last N days/weeks" — populated when using relative time range
    time_value           INT,
    time_unit            VARCHAR(16),
    -- Absolute window: "between date A and date B" — populated when using fixed date range
    time_start           TIMESTAMP,
    time_end             TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cohort_entries_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cohort_entries_referenced_cohort FOREIGN KEY (referenced_cohort_id) REFERENCES cohorts (id) ON DELETE SET NULL
);

CREATE TABLE cohort_entry_conditions
(
    id              BIGSERIAL PRIMARY KEY,
    cohort_entry_id BIGINT       NOT NULL,
    ordering        INT          NOT NULL DEFAULT 0,
    condition_type  VARCHAR(32)  NOT NULL,
    field           VARCHAR(255) NOT NULL,
    value           TEXT         NOT NULL,

    CONSTRAINT fk_cohort_entry_conditions_entry FOREIGN KEY (cohort_entry_id) REFERENCES cohort_entries (id) ON DELETE CASCADE
);

CREATE INDEX idx_cohort_entries_cohort_id ON cohort_entries (cohort_id);
CREATE INDEX idx_cohort_entry_conditions_entry_id ON cohort_entry_conditions (cohort_entry_id);
