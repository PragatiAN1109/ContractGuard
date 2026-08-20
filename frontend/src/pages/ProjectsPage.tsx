import { useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from '../api/projects';
import { ApiError } from '../api/client';
import { useAsync } from '../hooks/useAsync';
import { ErrorPanel } from '../components/ErrorPanel';
import { Loading } from '../components/Loading';

export function ProjectsPage() {
  const projects = useAsync(() => projectsApi.list(), []);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function createProject(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setSubmitError(null);
    try {
      await projectsApi.create(name.trim(), description.trim());
      setName('');
      setDescription('');
      projects.reload();
    } catch (e) {
      setSubmitError(e instanceof ApiError ? e : new ApiError(String(e), 0, null));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="stack">
      <header className="hero">
        <h1>ContractGuard</h1>
        <p className="thesis">A compatibility PASS does not imply operational safety.</p>
        <p className="muted">
          Structural compatibility and operational risk are analysed independently and reported
          separately. ContractGuard never merges them into a single verdict.
        </p>
      </header>

      <section className="section section-neutral">
        <header className="section-head">
          <h2>Projects</h2>
        </header>

        {projects.loading && <Loading label="Loading projects" />}
        {projects.error && <ErrorPanel error={projects.error} onRetry={projects.reload} />}

        {projects.data && projects.data.length === 0 && (
          <p className="muted">No projects yet. Create one below to get started.</p>
        )}

        {projects.data && projects.data.length > 0 && (
          <ul className="list">
            {projects.data.map((project) => (
              <li key={project.id} className="row">
                <Link className="row-main" to={`/projects/${project.id}`}>
                  <strong>{project.name}</strong>
                  {project.description && <span className="muted"> — {project.description}</span>}
                </Link>
                <span className="muted mono small">
                  created {new Date(project.createdAt).toLocaleString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="section section-neutral">
        <header className="section-head">
          <h2>New project</h2>
        </header>
        {submitError && <ErrorPanel error={submitError} />}
        <form className="form" onSubmit={createProject}>
          <label>
            Name
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="E-commerce Orders"
              required
            />
          </label>
          <label>
            Description <span className="muted">(optional)</span>
            <input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Order lifecycle events"
            />
          </label>
          <button className="btn" type="submit" disabled={submitting || !name.trim()}>
            {submitting ? 'Creating…' : 'Create project'}
          </button>
        </form>
      </section>
    </div>
  );
}
