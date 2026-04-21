CREATE TABLE user_settings (
    nav_ident  VARCHAR(32) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_setting_values (
    id           BIGSERIAL    PRIMARY KEY,
    nav_ident    VARCHAR(32)  NOT NULL REFERENCES user_settings (nav_ident),
    analytics_id VARCHAR(36),
    setting_key  VARCHAR(100) NOT NULL,
    value        TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (nav_ident, analytics_id, setting_key)
);
