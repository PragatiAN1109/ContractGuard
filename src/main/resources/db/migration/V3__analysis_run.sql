-- Durable analysis snapshots. A run records what ContractGuard knew at analysis time and is
-- never recomputed; every child row is owned by exactly one analysis_run.

CREATE TABLE analysis_run (
    id                       uuid         PRIMARY KEY,
    project_id               uuid         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    source_schema_version_id uuid         NOT NULL REFERENCES schema_version (id) ON DELETE CASCADE,
    target_schema_version_id uuid         NOT NULL REFERENCES schema_version (id) ON DELETE CASCADE,

    -- Denormalized so the history listing needs no joins. Safe: runs are immutable once written.
    source_version_number    integer      NOT NULL,
    target_version_number    integer      NOT NULL,
    backward_status          varchar(8),
    forward_status           varchar(8),
    full_status              varchar(8),
    finding_count            integer      NOT NULL DEFAULT 0,
    highest_severity         varchar(8)   NOT NULL DEFAULT 'NONE',

    status                   varchar(16)  NOT NULL,
    failure_code             varchar(64),
    failure_message          text,

    created_at               timestamptz  NOT NULL,
    started_at               timestamptz,
    completed_at             timestamptz
);

CREATE INDEX idx_analysis_run_project_created ON analysis_run (project_id, created_at DESC);

CREATE TABLE analysis_compatibility_result (
    id              uuid        PRIMARY KEY,
    analysis_run_id uuid        NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    mode            varchar(16) NOT NULL,
    status          varchar(8)  NOT NULL,
    summary         text        NOT NULL,
    position        integer     NOT NULL,
    CONSTRAINT uq_compatibility_result_mode UNIQUE (analysis_run_id, mode)
);

CREATE INDEX idx_compatibility_result_run ON analysis_compatibility_result (analysis_run_id);

CREATE TABLE analysis_compatibility_issue (
    id                        uuid         PRIMARY KEY,
    compatibility_result_id   uuid         NOT NULL
                                  REFERENCES analysis_compatibility_result (id) ON DELETE CASCADE,
    issue_type                varchar(64)  NOT NULL,
    path                      varchar(512),
    reason                    text         NOT NULL,
    position                  integer      NOT NULL
);

CREATE INDEX idx_compatibility_issue_result ON analysis_compatibility_issue (compatibility_result_id);

CREATE TABLE analysis_risk_finding (
    id              uuid         PRIMARY KEY,
    analysis_run_id uuid         NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    rule_id         varchar(64)  NOT NULL,
    severity        varchar(8)   NOT NULL,
    consumer        varchar(200) NOT NULL,
    schema_path     varchar(512) NOT NULL,
    reason          text         NOT NULL,
    position        integer      NOT NULL
);

CREATE INDEX idx_risk_finding_run ON analysis_risk_finding (analysis_run_id);

-- Key/value rather than per-rule columns: later rules carry different keys, so dedicated
-- columns would be mostly NULL.
CREATE TABLE analysis_finding_attribute (
    id              uuid         PRIMARY KEY,
    finding_id      uuid         NOT NULL REFERENCES analysis_risk_finding (id) ON DELETE CASCADE,
    attribute_key   varchar(64)  NOT NULL,
    attribute_value varchar(512),
    position        integer      NOT NULL,
    CONSTRAINT uq_finding_attribute_key UNIQUE (finding_id, attribute_key)
);

CREATE INDEX idx_finding_attribute_finding ON analysis_finding_attribute (finding_id);

-- One row per finding today. A separate table so a future rule with no source location simply
-- has no row, rather than a finding carrying NULL evidence columns.
CREATE TABLE analysis_source_evidence (
    id          uuid         PRIMARY KEY,
    finding_id  uuid         NOT NULL REFERENCES analysis_risk_finding (id) ON DELETE CASCADE,
    file_path   varchar(512) NOT NULL,
    file_name   varchar(255) NOT NULL,
    line_number integer      NOT NULL,
    snippet     text         NOT NULL,
    CONSTRAINT uq_source_evidence_finding UNIQUE (finding_id)
);
