-- First domain tables. Replaces the V1 baseline marker.

DROP TABLE IF EXISTS contractguard_baseline;

CREATE TABLE project (
    id          uuid          PRIMARY KEY,
    name        varchar(100)  NOT NULL,
    description varchar(1000),
    created_at  timestamptz   NOT NULL,
    CONSTRAINT uq_project_name UNIQUE (name)
);

CREATE TABLE schema_version (
    id             uuid         PRIMARY KEY,
    project_id     uuid         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    version_number integer      NOT NULL,
    schema_content text         NOT NULL,
    content_hash   varchar(64)  NOT NULL,
    created_at     timestamptz  NOT NULL,
    CONSTRAINT uq_schema_version_number UNIQUE (project_id, version_number),
    -- Enforces "no identical schema twice in a project" even under concurrent writes.
    CONSTRAINT uq_schema_version_hash   UNIQUE (project_id, content_hash)
);

CREATE INDEX idx_schema_version_project ON schema_version (project_id);
