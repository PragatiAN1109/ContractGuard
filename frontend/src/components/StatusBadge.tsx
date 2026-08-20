import type { AnalysisStatus, CompatibilityStatus, RiskSeverity } from '../api/types';

/** Colour is never the only signal — the label is always present. */
export function CompatBadge({ status }: { status: CompatibilityStatus | null }) {
  if (!status) return <span className="tag tag-neutral">n/a</span>;
  return <span className={status === 'PASS' ? 'tag tag-pass' : 'tag tag-fail'}>{status}</span>;
}

export function SeverityBadge({ severity }: { severity: RiskSeverity }) {
  const cls = severity === 'NONE' ? 'tag tag-neutral' : 'tag tag-risk';
  return <span className={cls}>{severity}</span>;
}

export function RunStatusBadge({ status }: { status: AnalysisStatus }) {
  const cls =
    status === 'COMPLETED' ? 'tag tag-pass' : status === 'FAILED' ? 'tag tag-fail' : 'tag tag-neutral';
  return <span className={cls}>{status}</span>;
}
