DROP TABLE IF EXISTS cohort_entry_conditions;
DROP TABLE IF EXISTS cohort_entries;

CREATE TABLE cohort_nodes
(
    id                    BIGSERIAL PRIMARY KEY,
    cohort_id             BIGINT       NOT NULL,
    parent_id             BIGINT,
    node_type             VARCHAR(32)  NOT NULL,
    ordering              INT          NOT NULL DEFAULT 0,

    -- GROUP
    combinator            VARCHAR(8),
    negated               BOOLEAN      NOT NULL DEFAULT FALSE,
    occurrence_operator    VARCHAR(32), -- reserved for a future pass (count >= N); unused for now
    occurrence_count       INT,

    -- CONDITION
    field                 VARCHAR(255),
    condition_type        VARCHAR(32),
    value                 TEXT,

    -- COHORT_REF
    referenced_cohort_id  BIGINT,

    -- SEQUENCE (this node) / role tag (when this node is a direct child of a SEQUENCE node)
    sequence_relation     VARCHAR(32),
    sequence_window_value INT,
    sequence_window_unit  VARCHAR(16),
    sequence_role         VARCHAR(16),

    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cohort_nodes_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cohort_nodes_parent FOREIGN KEY (parent_id) REFERENCES cohort_nodes (id) ON DELETE CASCADE
);

CREATE INDEX idx_cohort_nodes_cohort_id ON cohort_nodes (cohort_id);
CREATE INDEX idx_cohort_nodes_parent_id ON cohort_nodes (parent_id);
