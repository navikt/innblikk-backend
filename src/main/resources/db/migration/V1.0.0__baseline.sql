CREATE TABLE projects
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT NULL,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_projects_name UNIQUE (name)
);

CREATE TABLE dashboards
(
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT NULL,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dashboards_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_dashboards_project_name UNIQUE (project_id, name)
);

CREATE TABLE graphs
(
    id           BIGSERIAL PRIMARY KEY,
    dashboard_id BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    graph_type   VARCHAR(32) NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_graphs_dashboard FOREIGN KEY (dashboard_id) REFERENCES dashboards (id) ON DELETE CASCADE,
    CONSTRAINT uq_graphs_dashboard_name UNIQUE (dashboard_id, name)
);

CREATE TABLE queries
(
    id         BIGSERIAL PRIMARY KEY,
    graph_id   BIGINT       NOT NULL,
    name       VARCHAR(255) NOT NULL,
    sql_text   TEXT         NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_queries_graph FOREIGN KEY (graph_id) REFERENCES graphs (id) ON DELETE CASCADE,
    CONSTRAINT uq_queries_graph_name UNIQUE (graph_id, name)
);

CREATE TABLE changes
(
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL, -- project | dashboard | graph | query
    entity_id   BIGINT      NOT NULL, -- id of the changed entity
    change_type VARCHAR(32) NOT NULL, -- create | update | delete
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dashboards_project_id ON dashboards (project_id);
CREATE INDEX idx_graphs_dashboard_id ON graphs (dashboard_id);
CREATE INDEX idx_queries_graph_id ON queries (graph_id);
CREATE INDEX idx_changes_entity ON changes (entity_type, entity_id);
CREATE INDEX idx_changes_updated_at ON changes (updated_at DESC);