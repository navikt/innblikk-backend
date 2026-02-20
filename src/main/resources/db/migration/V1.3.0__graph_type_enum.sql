UPDATE graphs SET graph_type = UPPER(graph_type) WHERE graph_type IS NOT NULL;

ALTER TABLE graphs ADD CONSTRAINT chk_graphs_graph_type
    CHECK (graph_type IS NULL OR graph_type IN ('LINE', 'BAR', 'PIE', 'TABLE'));


