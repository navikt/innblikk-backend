-- Nothing shipped yet on cohorts — wipe rather than migrate the tree shape.
-- The "Tidspunkt" (created_at) condition changed from two separate GTE/LTE
-- conditions to one BETWEEN condition holding a {from, to} JSON value (see
-- ComparisonOperator.BETWEEN and CohortDateTimeEditor.tsx), which old rows
-- don't match.
TRUNCATE TABLE cohort_nodes, cohorts RESTART IDENTITY CASCADE;
