CREATE TABLE sidegroups
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    include    text[]       NOT NULL DEFAULT '{}',
    exclude    text[]       NOT NULL DEFAULT '{}',
    exact      text[]       NOT NULL DEFAULT '{}',
    start_with text[]       NOT NULL DEFAULT '{}',
    end_with   text[]       NOT NULL DEFAULT '{}',
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sidegroups_name UNIQUE (name)
);