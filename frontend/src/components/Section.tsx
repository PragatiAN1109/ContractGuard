import type { ReactNode } from 'react';

export function Section({
  title,
  accent,
  subtitle,
  children,
}: {
  title: string;
  accent?: 'structural' | 'risk' | 'neutral';
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <section className={`section section-${accent ?? 'neutral'}`}>
      <header className="section-head">
        <h2>{title}</h2>
        {subtitle && <p className="muted">{subtitle}</p>}
      </header>
      {children}
    </section>
  );
}
