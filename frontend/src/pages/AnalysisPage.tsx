import { Link, useParams } from 'react-router-dom';
import { analysesApi } from '../api/analyses';
import { rolloutApi } from '../api/rollout';
import { useAsync } from '../hooks/useAsync';
import { ErrorPanel } from '../components/ErrorPanel';
import { Loading } from '../components/Loading';
import { Section } from '../components/Section';
import { CompatBadge, RunStatusBadge, SeverityBadge } from '../components/StatusBadge';
import type {
  CompatibilityModeResult,
  ConsumerAnalysisContext,
  RiskFinding,
  RolloutPlan,
} from '../api/types';

const SOURCE_TYPE_LABEL: Record<string, string> = {
  BUILT_IN_SAMPLE: 'built-in sample',
};

/** Groups findings by consumer while preserving backend ordering within each group. */
function groupByConsumer(findings: RiskFinding[]): Array<[string, RiskFinding[]]> {
  const groups = new Map<string, RiskFinding[]>();
  findings.forEach((finding) => {
    const existing = groups.get(finding.consumer);
    if (existing) existing.push(finding);
    else groups.set(finding.consumer, [finding]);
  });
  return [...groups.entries()];
}

function ConsumerContext({ context }: { context: ConsumerAnalysisContext }) {
  if (context.consumerCount === 0) {
    return (
      <p className="muted">
        No consumer source was registered for this schema, so no operational-risk rule could run.
        A clean risk result here means nothing was examined.
      </p>
    );
  }

  const sourceLabel = context.sourceTypes
    .map((type) => SOURCE_TYPE_LABEL[type] ?? type)
    .join(', ');

  return (
    <>
      <p className="context-lead">
        {`Operational-risk analysis used ${context.consumerCount} registered consumer` +
          `${context.consumerCount === 1 ? ' source' : ' sources'} (${sourceLabel}).`}
      </p>
      <ul className="consumer-list">
        {context.consumers.map((consumer) => (
          <li key={consumer.name}>
            <div className="consumer-head">
              <span className="consumer">{consumer.name}</span>
              <span className="tag tag-neutral">{SOURCE_TYPE_LABEL[consumer.sourceType] ?? consumer.sourceType}</span>
            </div>
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
      <p className="muted small">
        ContractGuard analyses only consumer source that is registered with it. It does not scan
        arbitrary repositories today.
      </p>
    </>
  );
}

const STRATEGY_LABEL: Record<RolloutPlan['strategy'], string> = {
  CONSUMER_FIRST: 'Consumer first',
  BLOCKED_BY_COMPATIBILITY: 'Blocked by compatibility',
  // Deliberately not "Safe".
  NO_CONSTRAINT_IDENTIFIED: 'No rollout constraint identified',
};

function CompatibilityMode({ result }: { result: CompatibilityModeResult | null }) {
  if (!result) return null;
  return (
    <article className="mode">
      <div className="mode-head">
        <h3>{result.mode}</h3>
        <CompatBadge status={result.status} />
      </div>
      <p className="mode-summary">{result.summary}</p>
      {result.issues.length > 0 && (
        <ul className="issues">
          {result.issues.map((issue, index) => (
            <li key={`${issue.issueType}-${index}`}>
              <div className="issue-head">
                <span className="tag tag-fail">{issue.issueType}</span>
                {issue.path && <code className="path">{issue.path}</code>}
              </div>
              <p className="muted">{issue.reason}</p>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

function Finding({ finding }: { finding: RiskFinding }) {
  const newSymbol = finding.attributes.newSymbol;
  const fallbackSymbol = finding.attributes.fallbackSymbol;

  return (
    <article className="finding">
      <div className="finding-head">
        <span className="tag tag-risk">{finding.severity}</span>
        <code className="rule">{finding.ruleId}</code>
        <span className="consumer">{finding.consumer}</span>
      </div>

      <dl className="finding-meta">
        <div>
          <dt>Schema path</dt>
          <dd>
            <code>{finding.schemaPath}</code>
          </dd>
        </div>
        {newSymbol && fallbackSymbol && (
          <div>
            <dt>Fallback</dt>
            <dd>
              <code>{newSymbol}</code> <span className="arrow">→</span> <code>{fallbackSymbol}</code>
            </dd>
          </div>
        )}
      </dl>

      <p className="finding-reason">{finding.reason}</p>

      {finding.evidence ? (
        <div className="evidence">
          <div className="evidence-head">
            <span className="evidence-label">Source evidence</span>
            <code className="evidence-loc">
              {finding.evidence.sourceFile}:{finding.evidence.line}
            </code>
          </div>
          <pre className="evidence-code">
            <code>{finding.evidence.snippet}</code>
          </pre>
          <p className="muted small mono">{finding.evidence.filePath}</p>
        </div>
      ) : (
        <p className="muted small">No source location recorded for this finding.</p>
      )}
    </article>
  );
}

export function AnalysisPage() {
  const { analysisId = '' } = useParams();
  const analysis = useAsync(() => analysesApi.get(analysisId), [analysisId]);
  const rollout = useAsync(() => rolloutApi.get(analysisId), [analysisId]);

  if (analysis.loading) return <Loading label="Loading analysis" />;
  if (analysis.error) return <ErrorPanel error={analysis.error} onRetry={analysis.reload} />;
  if (!analysis.data) return null;

  const run = analysis.data;
  const risk = run.operationalRisk;
  const consumerGroups = groupByConsumer(risk.findings);

  return (
    <div className="stack">
      <nav className="breadcrumb">
        <Link to="/">Projects</Link>
        <span className="muted"> / </span>
        <Link to={`/projects/${run.projectId}`}>Project</Link>
        <span className="muted"> / </span>
        <span>Analysis</span>
      </nav>

      <header className="hero hero-compact">
        <h1>
          v{run.sourceVersion} <span className="arrow">→</span> v{run.targetVersion}
        </h1>
        <p className="muted mono small">
          <RunStatusBadge status={run.status} /> &nbsp;{run.analysisId}
        </p>
        <p className="muted small">
          Persisted snapshot, created {new Date(run.createdAt).toLocaleString()}
          {run.completedAt && ` · completed ${new Date(run.completedAt).toLocaleString()}`}
        </p>
      </header>

      {run.status === 'FAILED' && (
        <div className="panel panel-error">
          <div className="panel-error-head">
            <span className="tag tag-fail">FAILED</span>
            <strong>{run.failureCode}</strong>
          </div>
          <p className="panel-error-detail">{run.failureMessage}</p>
        </div>
      )}

      <Section
        title="Structural compatibility"
        accent="structural"
        subtitle="Derived from the two schemas only."
      >
        <div className="modes">
          <CompatibilityMode result={run.compatibility.backward} />
          <CompatibilityMode result={run.compatibility.forward} />
          <CompatibilityMode result={run.compatibility.full} />
        </div>
      </Section>

      <Section
        title="Consumer analysis context"
        accent="risk"
        subtitle="Which consumer source took part in this analysis, recorded at the time it ran."
      >
        <ConsumerContext context={run.consumerAnalysis} />
      </Section>

      <Section
        title="Operational risk"
        accent="risk"
        subtitle="Derived from registered consumer source and schema-change context. Independent of the compatibility result above."
      >
        <div className="risk-summary">
          <div>
            <span className="stat-label">Highest severity</span>
            <SeverityBadge severity={risk.overallSeverity} />
          </div>
          <div>
            <span className="stat-label">Findings</span>
            <strong>{risk.findingCount}</strong>
          </div>
        </div>

        {risk.findings.length === 0 ? (
          <p className="muted">
            No implemented risk rule fired. That is not proof that the change is safe to deploy.
          </p>
        ) : (
          <>
            <p className="muted small">
              {`${risk.findingCount} evidence-backed finding${risk.findingCount === 1 ? '' : 's'}` +
                ` in ${consumerGroups.length} affected consumer${consumerGroups.length === 1 ? '' : 's'}.` +
                ' Each finding is a distinct source location, not a duplicate record.'}
            </p>
            {consumerGroups.map(([consumer, findings]) => (
              <div key={consumer} className="consumer-group">
                <h3 className="consumer-group-head">
                  <span className="consumer">{consumer}</span>
                  <span className="muted small">
                    {findings.length} evidence location{findings.length === 1 ? '' : 's'}
                  </span>
                </h3>
                <div className="findings">
                  {findings.map((finding, index) => (
                    <Finding key={`${finding.ruleId}-${index}`} finding={finding} />
                  ))}
                </div>
              </div>
            ))}
          </>
        )}
      </Section>

      <Section title="Rollout guidance" accent="neutral" subtitle="Derived from the persisted analysis snapshot.">
        {rollout.loading && <Loading label="Loading rollout guidance" />}
        {rollout.error && (
          <>
            {rollout.error.status === 409 ? (
              <p className="muted">
                Rollout guidance is only available for a COMPLETED analysis. This run is{' '}
                {run.status}.
              </p>
            ) : (
              <ErrorPanel error={rollout.error} onRetry={rollout.reload} />
            )}
          </>
        )}

        {rollout.data && (
          <div className="rollout">
            <div className="rollout-head">
              <span className="tag tag-neutral">{STRATEGY_LABEL[rollout.data.strategy]}</span>
              <code className="small">{rollout.data.strategy}</code>
            </div>
            <p>{rollout.data.summary}</p>

            {rollout.data.steps.length > 0 && (
              <ol className="steps">
                {rollout.data.steps.map((step) => (
                  <li key={step.order}>
                    <div className="step-head">
                      <code className="action">{step.action}</code>
                      <span className="target">{step.target}</span>
                    </div>
                    {step.reason && <p className="muted small">{step.reason}</p>}
                  </li>
                ))}
              </ol>
            )}

            {rollout.data.limitations.length > 0 && (
              <div className="limitations">
                <span className="stat-label">Limitations</span>
                <ul>
                  {rollout.data.limitations.map((limitation) => (
                    <li key={limitation} className="muted small">
                      {limitation}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </Section>
    </div>
  );
}
