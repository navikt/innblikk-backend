ALTER TABLE queries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE canvas_locks (
    frame_graph_id INTEGER NOT NULL,
    dashboard_id BIGINT NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    owner_label VARCHAR(255) NOT NULL DEFAULT 'En kollega',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (frame_graph_id, dashboard_id)
);

CREATE INDEX idx_canvas_locks_expires ON canvas_locks (expires_at);
