import type { ApiError } from '../api/client';

/** Renders an RFC 9457 problem response in a readable form. */
export function ErrorPanel({ error, onRetry }: { error: ApiError; onRetry?: () => void }) {
  const problem = error.problem;
  const fieldErrors = Object.entries(error.fieldErrors);

  return (
    <div className="panel panel-error" role="alert">
      <div className="panel-error-head">
        <span className="tag tag-fail">{problem?.status ?? 'ERROR'}</span>
        <strong>{problem?.title ?? 'Request failed'}</strong>
      </div>
      <p className="panel-error-detail">{problem?.detail ?? error.message}</p>

      {fieldErrors.length > 0 && (
        <ul className="field-errors">
          {fieldErrors.map(([field, message]) => (
            <li key={field}>
              <code>{field}</code> — {message}
            </li>
          ))}
        </ul>
      )}

      {problem?.analysisId && (
        <p className="muted">
          Analysis <code>{problem.analysisId}</code> was recorded as FAILED and can still be opened.
        </p>
      )}

      {onRetry && (
        <button className="btn btn-quiet" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
