-- Create graph_categories table
CREATE TABLE graph_categories
(
    id           BIGSERIAL PRIMARY KEY,
    dashboard_id BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    ordering     INT          NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_graph_categories_dashboard FOREIGN KEY (dashboard_id) REFERENCES dashboards (id) ON DELETE CASCADE,
    CONSTRAINT uq_graph_categories_dashboard_name UNIQUE (dashboard_id, name)
);

CREATE INDEX idx_graph_categories_dashboard_id ON graph_categories (dashboard_id);

-- Insert a default "General" category for each dashboard that has graphs
INSERT INTO graph_categories (dashboard_id, name, ordering)
SELECT DISTINCT dashboard_id, 'General', 0
FROM graphs;

-- Add category_id column to graphs
ALTER TABLE graphs ADD COLUMN category_id BIGINT;

-- Populate category_id from the default categories
UPDATE graphs
SET category_id = gc.id
FROM graph_categories gc
WHERE graphs.dashboard_id = gc.dashboard_id;

-- Make category_id NOT NULL and add FK
ALTER TABLE graphs ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE graphs ADD CONSTRAINT fk_graphs_category
    FOREIGN KEY (category_id) REFERENCES graph_categories (id) ON DELETE CASCADE;

CREATE INDEX idx_graphs_category_id ON graphs (category_id);

-- Drop old dashboard-scoped constraints and column
ALTER TABLE graphs DROP CONSTRAINT uq_graphs_dashboard_name;
DROP INDEX idx_graphs_dashboard_id;
ALTER TABLE graphs DROP CONSTRAINT fk_graphs_dashboard;
ALTER TABLE graphs DROP COLUMN dashboard_id;

-- Add new unique constraint scoped to category
ALTER TABLE graphs ADD CONSTRAINT uq_graphs_category_name UNIQUE (category_id, name);

