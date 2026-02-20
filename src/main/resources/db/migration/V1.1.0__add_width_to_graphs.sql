ALTER TABLE graphs ADD COLUMN width INT NOT NULL DEFAULT 50;

ALTER TABLE graphs ADD CONSTRAINT chk_graphs_width CHECK (width >= 0 AND width <= 100);

