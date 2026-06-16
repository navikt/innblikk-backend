# Innblikk

Analytics dashboard platform for NAV. Lets teams build charts and saved queries over Umami/BigQuery data, grouped by website, and filter results using reusable segment definitions.

## Language

**Cohort**:
A named, reusable segment definition scoped to a website — evaluated dynamically at query time, not materialized.
_Avoid_: Segment, filter, user group, saved filter

**Website**:
An Umami analytics property identified by a UUID `websiteId`. Top-level scope for cohorts, sidegroups, and chart data.
_Avoid_: Site, property, tenant

**Cohort Entry**:
A single rule within a cohort — either a condition group or a cohort reference. Entries are AND-chained at the cohort level.
_Avoid_: Rule, criteria, filter row

**Condition**:
A single field predicate within a cohort entry (e.g. `url_path CONTAINS "/skjema"`).
_Avoid_: Filter, clause

**Cohort Reference**:
A cohort entry that points to another cohort, meaning "users who also qualify for cohort X".
_Avoid_: Nested cohort, sub-cohort

**Grafbygger**:
The chart builder UI where users compose queries and apply cohorts as data filters.
_Avoid_: Chart builder, query builder

**Sidegroup**:
A URL-path grouping rule for a website — bundles matching paths into a named group for aggregation.
_Avoid_: URL group, path group

## Relationships

- A **Website** has zero or more **Cohorts**
- A **Cohort** has one or more **Cohort Entries**
- A **Cohort Entry** has zero or more **Conditions** (when it is a condition group) OR exactly one **Cohort Reference** (when `inCohort = true`)
- A **Cohort** is applied in **Grafbygger** to scope BigQuery queries to matching users

## Flagged ambiguities

- "segment" was used loosely in `cohortSqlResolver.ts` (`SegmentDefinition`) — in domain language this is still a **Cohort**; `SegmentDefinition` is an internal SQL-layer type only.
- **Cohort** is dynamic (re-evaluated per query) — it is NOT a snapshot/materialized group.

## Architectural decisions

- **Time filtering is a Condition, not an entry-level field.** `created_at >= X` is expressed as a regular Condition within a group, using `GREATER_THAN_OR_EQUAL` or `LESS_THAN_OR_EQUAL`. The earlier `time_value`/`time_unit`/`time_start`/`time_end` columns on `cohort_entries` were removed. Users compose time ranges within a group alongside other conditions — each group filters on a single event row satisfying ALL its conditions simultaneously.
- **Relative time values are serialized as JSON in the condition `value` column.** A relative date (e.g. "last 30 days") is stored as a JSON-encoded `RelativeDateTimeValue` object (from `@react-querybuilder/datetime`). `cohortSqlResolver.ts` deserializes it at query-build time and emits a BigQuery `TIMESTAMP_SUB`/`TIMESTAMP_ADD` expression. Absolute dates are stored as ISO strings and emitted as `TIMESTAMP('...')`. Both are injected raw (not parameterized) by the existing `buildSegmentFilterCondition` pipeline.
