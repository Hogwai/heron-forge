import type { ReactNode } from "react";
import type { WidgetProps } from "../types";
import { useWidgetData } from "../useWidgetData";
import { EmptyState, ErrorState, Skeleton, WidgetFrame } from "../WidgetFrame";

export function KpiCard({ widget }: Readonly<WidgetProps>) {
  const { rows, error, loading } = useWidgetData(widget.path);

  let body: ReactNode;
  if (loading) {
    body = <Skeleton kind="kpi" />;
  } else if (error) {
    body = <ErrorState message={error} />;
  } else if (rows.length === 0) {
    body = <EmptyState />;
  } else {
    body = (
      <>
        <p className="mt-1.5 text-[46px] font-bold leading-[1.05] tracking-[-0.02em] text-ink tabular-nums">
          {rows.length}
        </p>
        <p className="mt-2 font-mono text-[10.5px] uppercase tracking-[0.08em] text-ink-3">
          records matched
        </p>
      </>
    );
  }

  return (
    <WidgetFrame
      title={widget.title}
      fill
      busy={loading}
      meta={
        <span className="inline-flex items-center gap-1.5">
          <span className="inline-block size-1.5 animate-pulse-ring rounded-full bg-ok" />
          <span>live</span>
        </span>
      }
    >
      {body}
    </WidgetFrame>
  );
}
