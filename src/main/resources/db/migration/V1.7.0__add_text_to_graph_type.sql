ALTER TABLE graphs DROP CONSTRAINT chk_graphs_graph_type;

ALTER TABLE graphs ADD CONSTRAINT chk_graphs_graph_type
    CHECK (graph_type IS NULL OR graph_type IN ('LINE', 'BAR', 'PIE', 'TABLE', 'TEXT'));
