CREATE TABLE cohorts
(
    id          BIGSERIAL PRIMARY KEY,
    website_id  UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cohorts_name UNIQUE (name)
);

CREATE TABLE cohort_entries
(
    id              BIGSERIAL PRIMARY KEY,
    cohort_id       BIGINT      NOT NULL,
    condition       VARCHAR(32) NOT NULL,
    operator        VARCHAR(32),
    time_start      TIMESTAMP,
    time_end        TIMESTAMP,
    time_range_name VARCHAR(64),
    time_range_unit VARCHAR(64),
    ordering        INT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cohort_entries_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts (id) ON DELETE CASCADE
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
