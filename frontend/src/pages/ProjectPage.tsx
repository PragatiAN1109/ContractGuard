import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { analysesApi } from '../api/analyses';
import { projectsApi } from '../api/projects';
import { schemasApi } from '../api/schemas';
import { ecommerceOrderSample } from '../samples/ecommerceOrder';
import { useAsync } from '../hooks/useAsync';
import { ErrorPanel } from '../components/ErrorPanel';
import { Loading } from '../components/Loading';
import { CompatBadge, RunStatusBadge, SeverityBadge } from '../components/StatusBadge';

/**
 * Pre-flight view of the consumer sources an analysis would examine, so operational-risk findings
 * are never a surprise. Reads the registry, which is why it is labelled as "will be".
 */
function ConsumerSourcePreview({
  projectId,
  schemaVersionId,
}: {
  projectId: string;
  schemaVersionId: string;
}) {
  const sources = useAsync(
    () =>
      schemaVersionId
        ? schemasApi.consumerSources(projectId, schemaVersionId)
        : Promise.resolve(null),
    [projectId, schemaVersionId],
  );

  if (!schemaVersionId) {
    return (
      <p className="muted small">
        Select a source version to see which registered consumer sources would be analysed.
      </p>
    );
  }
  if (sources.loading) return <p className="muted small">Loading consumer sources…</p>;
  if (sources.error || !sources.data) return null;

  const { consumerCount, consumers, schemaFullName } = sources.data;

  return (
    <div className="preflight">
      <span className="stat-label">Consumer sources included in operational-risk analysis</span>
      {consumerCount === 0 ? (
        <p className="muted small">
          {`No consumer source is registered for ${schemaFullName}. Operational-risk analysis will ` +
            'report nothing, which is not the same as finding nothing.'}
        </p>
      ) : (
        <>
          <p className="muted small">
            {`${consumerCount} registered consumer source${consumerCount === 1 ? '' : 's'} for ` +
              `${schemaFullName} will be scanned by the rule ENUM_SEMANTIC_FALLBACK_RISK.`}
          </p>
          <ul className="consumer-list">
            {consumers.map((consumer) => (
              <li key={consumer.name}>
                <div className="consumer-head">
                  <span className="consumer">{consumer.name}</span>
                  <span className="tag tag-neutral">built-in sample</span>
                </div>
                {consumer.description && <p className="muted small">{consumer.description}</p>}
                <ul className="file-list">
                  {consumer.sourceFiles.map((file) => (
                    <li key={file} className="muted small mono">
                      {file}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

export function ProjectPage() {
  const { projectId = '' } = useParams();
  const navigate = useNavigate();

  const project = useAsync(() => projectsApi.get(projectId), [projectId]);
  const schemas = useAsync(() => schemasApi.list(projectId), [projectId]);
  const history = useAsync(() => analysesApi.history(projectId), [projectId]);

  const [schemaContent, setSchemaContent] = useState('');
  const [source, setSource] = useState('');
  const [target, setTarget] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  function asApiError(e: unknown) {
    return e instanceof ApiError ? e : new ApiError(String(e), 0, null);
  }

  async function addSchema(event: React.FormEvent) {
    event.preventDefault();
    setBusy('schema');
    setError(null);
    try {
      await schemasApi.create(projectId, schemaContent);
      setSchemaContent('');
      schemas.reload();
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setBusy(null);
    }
  }

  /** Posts the repository's real sample schemas through the real API. */
  async function loadSample() {
    setBusy('sample');
    setError(null);
    try {
      for (const content of ecommerceOrderSample.versions) {
        await schemasApi.create(projectId, content);
      }
      schemas.reload();
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setBusy(null);
    }
  }

  async function runAnalysis(event: React.FormEvent) {
    event.preventDefault();
    setBusy('analysis');
    setError(null);
    try {
      const run = await analysesApi.create(projectId, source, target);
      navigate(`/analyses/${run.analysisId}`);
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setBusy(null);
    }
  }

  const versions = schemas.data ?? [];

  return (
    <div className="stack">
      <nav className="breadcrumb">
        <Link to="/">Projects</Link>
        <span className="muted"> / </span>
        <span>{project.data?.name ?? projectId}</span>
      </nav>

      {project.loading && <Loading label="Loading project" />}
      {project.error && <ErrorPanel error={project.error} onRetry={project.reload} />}
      {project.data && (
        <header className="hero hero-compact">
          <h1>{project.data.name}</h1>
          {project.data.description && <p className="muted">{project.data.description}</p>}
        </header>
      )}

      {error && <ErrorPanel error={error} />}

      <section className="section section-neutral">
        <header className="section-head">
          <h2>Schema versions</h2>
          <p className="muted">Avro records, immutable once stored.</p>
        </header>

        {schemas.loading && <Loading label="Loading schema versions" />}
        {schemas.error && <ErrorPanel error={schemas.error} onRetry={schemas.reload} />}

        {!schemas.loading && versions.length === 0 && (
          <div className="empty">
            <p className="muted">No schema versions yet.</p>
            <button className="btn btn-quiet" onClick={loadSample} disabled={busy !== null}>
              {busy === 'sample' ? 'Loading sample…' : 'Load sample schemas (order-v1 → order-v2)'}
            </button>
            <p className="muted small">
              Development convenience. Posts the repository&apos;s real sample files through the
              normal API.
            </p>
          </div>
        )}

        {versions.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Version</th>
                <th>Content hash</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {versions.map((version) => (
                <tr key={version.id}>
                  <td>v{version.versionNumber}</td>
                  <td className="mono small">{version.contentHash.slice(0, 12)}…</td>
                  <td className="muted small">{new Date(version.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="section section-neutral">
        <header className="section-head">
          <h2>Add schema version</h2>
        </header>
        <form className="form" onSubmit={addSchema}>
          <label>
            Avro schema JSON <span className="muted">(record root required)</span>
            <textarea
              value={schemaContent}
              onChange={(e) => setSchemaContent(e.target.value)}
              rows={10}
              spellCheck={false}
              placeholder='{"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[…]}'
              required
            />
          </label>
          <button className="btn" type="submit" disabled={busy !== null || !schemaContent.trim()}>
            {busy === 'schema' ? 'Storing…' : 'Store schema version'}
          </button>
        </form>
      </section>

      <section className="section section-neutral">
        <header className="section-head">
          <h2>Run analysis</h2>
          <p className="muted">
            Runs the structural and operational analyses and persists one immutable snapshot.
          </p>
        </header>
        <form className="form form-inline" onSubmit={runAnalysis}>
          <label>
            Source (baseline)
            <select value={source} onChange={(e) => setSource(e.target.value)} required>
              <option value="">Select…</option>
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  v{v.versionNumber}
                </option>
              ))}
            </select>
          </label>
          <span className="arrow">→</span>
          <label>
            Target (proposed)
            <select value={target} onChange={(e) => setTarget(e.target.value)} required>
              <option value="">Select…</option>
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  v{v.versionNumber}
                </option>
              ))}
            </select>
          </label>
          <button className="btn btn-primary" type="submit" disabled={busy !== null || !source || !target}>
            {busy === 'analysis' ? 'Analysing…' : 'Run analysis'}
          </button>
        </form>

        <ConsumerSourcePreview projectId={projectId} schemaVersionId={source} />
      </section>

      <section className="section section-neutral">
        <header className="section-head">
          <h2>Analysis history</h2>
          <p className="muted">Stored snapshots, newest first. Opening one does not re-run it.</p>
        </header>

        {history.loading && <Loading label="Loading history" />}
        {history.error && <ErrorPanel error={history.error} onRetry={history.reload} />}
        {history.data && history.data.length === 0 && (
          <p className="muted">No analyses recorded for this project yet.</p>
        )}

        {history.data && history.data.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Versions</th>
                <th>Status</th>
                <th>Backward</th>
                <th>Forward</th>
                <th>Full</th>
                <th>Risk</th>
                <th>Findings</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {history.data.map((run) => (
                <tr key={run.analysisId}>
                  <td>
                    <Link to={`/analyses/${run.analysisId}`}>
                      v{run.sourceVersion} → v{run.targetVersion}
                    </Link>
                  </td>
                  <td>
                    <RunStatusBadge status={run.status} />
                  </td>
                  <td>
                    <CompatBadge status={run.compatibility.backward} />
                  </td>
                  <td>
                    <CompatBadge status={run.compatibility.forward} />
                  </td>
                  <td>
                    <CompatBadge status={run.compatibility.full} />
                  </td>
                  <td>
                    <SeverityBadge severity={run.highestSeverity} />
                  </td>
                  <td>{run.findingCount}</td>
                  <td className="muted small">{new Date(run.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
