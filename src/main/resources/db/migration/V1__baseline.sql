-- Baseline migration. Creates no domain tables (see docs/architecture.md for the planned model)
-- and exists only so the Flyway wiring is verifiable against a fresh database.
-- Drop this table in the migration that adds the first real one.

CREATE TABLE contractguard_baseline (
    id             smallint    NOT NULL PRIMARY KEY,
    initialised_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT contractguard_baseline_single_row CHECK (id = 1)
);

INSERT INTO contractguard_baseline (id) VALUES (1);

COMMENT ON TABLE contractguard_baseline IS
    'Placeholder from the ContractGuard baseline migration. Drop it in the migration that creates the first domain table.';
