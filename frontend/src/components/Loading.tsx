export function Loading({ label = 'Loading' }: { label?: string }) {
  return (
    <p className="muted" role="status">
      {label}…
    </p>
  );
}
