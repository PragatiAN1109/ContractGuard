-- Explicitly registered consumer source, so operational-risk findings are traceable to code the
-- user supplied rather than only to bundles shipped on the classpath.
--
-- Rows are immutable revisions. Re-registering a service name inserts a new row and stamps
-- superseded_at on the previous one, so an AnalysisRun can keep pointing at the exact revision it
-- analysed instead of silently following later edits.

CREATE TABLE consumer_source (
    id              uuid         PRIMARY KEY,
    project_id      uuid         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    service_name    varchar(200) NOT NULL,
    consumes_schema varchar(512) NOT NULL,
    source_type     varchar(32)  NOT NULL,
    -- SHA-256 over the sorted (path, content) pairs: the revision identifier.
    revision_hash   varchar(64)  NOT NULL,
    file_count      integer      NOT NULL,
    description     varchar(1000),
    created_at      timestamptz  NOT NULL,
    -- NULL means this is the active revision for the service.
    superseded_at   timestamptz
);

-- One active revision per service per project. Superseded rows are exempt, so history accumulates.
CREATE UNIQUE INDEX uq_consumer_source_active
    ON consumer_source (project_id, service_name)
    WHERE superseded_at IS NULL;

CREATE INDEX idx_consumer_source_lookup
    ON consumer_source (project_id, consumes_schema, superseded_at);

CREATE TABLE consumer_source_file (
    id                uuid         PRIMARY KEY,
    consumer_source_id uuid        NOT NULL REFERENCES consumer_source (id) ON DELETE CASCADE,
    path              varchar(512) NOT NULL,
    content           text         NOT NULL,
    position          integer      NOT NULL,
    CONSTRAINT uq_consumer_source_file_path UNIQUE (consumer_source_id, path)
);

CREATE INDEX idx_consumer_source_file_source ON consumer_source_file (consumer_source_id);

-- Provenance on the analysis snapshot. Nullable because built-in sample bundles are not rows in
-- consumer_source; their provenance is the source type plus the revision hash of their content.
ALTER TABLE analysis_analysed_consumer
    ADD COLUMN consumer_source_id uuid REFERENCES consumer_source (id) ON DELETE SET NULL,
    ADD COLUMN revision_hash      varchar(64);
