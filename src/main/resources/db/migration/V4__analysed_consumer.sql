-- Which consumers an analysis examined, whether or not they produced findings.
--
-- Without this, a stored severity of NONE is uninterpretable: "no findings" only means something
-- if you know what was looked at. Reading the live registry instead would show today's consumers
-- beside a historical snapshot, which is exactly the kind of silent drift ContractGuard exists to
-- surface.

CREATE TABLE analysis_analysed_consumer (
    id              uuid         PRIMARY KEY,
    analysis_run_id uuid         NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    consumer_name   varchar(200) NOT NULL,
    source_type     varchar(32)  NOT NULL,
    -- Newline-separated paths. A child table would be more normalized, but this list is short,
    -- read whole, and never queried by element; string_to_array covers the rare ad-hoc case.
    source_files    text         NOT NULL DEFAULT '',
    position        integer      NOT NULL,
    CONSTRAINT uq_analysed_consumer_name UNIQUE (analysis_run_id, consumer_name)
);

CREATE INDEX idx_analysed_consumer_run ON analysis_analysed_consumer (analysis_run_id);
