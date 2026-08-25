import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { ReactNode } from "react";
import type { WidgetProps } from "../types";
import { useWidgetData } from "../useWidgetData";
import { EmptyState, ErrorState, Skeleton, WidgetFrame } from "../WidgetFrame";

interface BarEntry {
  category: string;
  value: number;
  /** Per-bar color consumed by <Bar> — replaces the deprecated Cell component. */
  fill: string;
}

function project(rows: unknown[]): { category: string; value: number }[] {
  const objects = rows.filter(
    (row): row is Record<string, unknown> => typeof row === "object" && row !== null,
  );
  if (objects.length === 0) {
    return [];
  }
  const keys = Object.keys(objects[0]);
  const categoryKey = keys.find((key) => typeof objects[0][key] === "string") ?? keys[0];
  const valueKey = keys.find((key) => typeof objects[0][key] === "number") ?? keys.at(-1);
  if (valueKey === undefined) {
    return [];
  }
  return objects.map((row) => ({
    category: String(row[categoryKey]),
    value: Number(row[valueKey] ?? 0),
  }));
}

/* Steel-blue magnitude ramp: darker = larger value. Neutral encoding.
   Recharts SVG internals stay as inline props / TS constants — Tailwind
   does not reach them. */
const RAMP_LOW = [185, 205, 224];
const RAMP_HIGH = [45, 91, 138];

function rampColor(ratio: number): string {
  const t = Number.isFinite(ratio) ? Math.min(1, Math.max(0, ratio)) : 0.5;
  const [r, g, b] = RAMP_LOW.map((low, index) => {
    const high = RAMP_HIGH[index];
    return Math.round(low + (high - low) * t);
  });
  return `rgb(${r}, ${g}, ${b})`;
}

/** Attach the magnitude-ramp fill to each entry; <Bar> consumes it directly. */
function withBarFills(entries: { category: string; value: number }[]): BarEntry[] {
  const max = Math.max(...entries.map((entry) => entry.value));
  const hasScale = Number.isFinite(max) && max !== 0;
  return entries.map((entry) => ({
    ...entry,
    fill: rampColor(hasScale ? entry.value / max : 0.5),
  }));
}

interface TooltipView {
  active?: boolean;
  label?: string | number;
  payload?: ReadonlyArray<{ value?: number | string }>;
}

function RatioTooltip({ active, label, payload }: Readonly<TooltipView>) {
  if (!active || !payload || payload.length === 0) {
    return null;
  }
  const value = payload[0]?.value;
  return (
    <div className="flex flex-col gap-0.5 rounded-md border border-line bg-surface px-2.5 py-2 font-mono shadow-card-hover">
      <span className="text-[10px] uppercase tracking-[0.07em] text-ink-3">{label}</span>
      <span className="text-[13px] font-semibold tabular-nums text-ink">
        {typeof value === "number" ? value.toLocaleString("en-US") : String(value ?? "—")}
      </span>
    </div>
  );
}

export function RatioChart({ widget }: Readonly<WidgetProps>) {
  const { rows, error, loading } = useWidgetData(widget.path);
  const data = withBarFills(project(rows));

  let body: ReactNode;
  if (loading) {
    body = <Skeleton kind="chart" />;
  } else if (error) {
    body = <ErrorState message={error} />;
  } else if (data.length === 0) {
    body = <EmptyState />;
  } else {
    body = (
      <div className="pt-1">
        <ResponsiveContainer width="100%" height={252}>
          <BarChart data={data} margin={{ top: 24, right: 8, bottom: 0, left: -14 }}>
            <CartesianGrid vertical={false} stroke="var(--color-chart-grid)" />
            <XAxis
              dataKey="category"
              tickLine={false}
              axisLine={{ stroke: "var(--color-line-strong)" }}
              tick={{ fill: "var(--color-ink-2)", fontSize: 11, fontFamily: "var(--font-mono)" }}
            />
            <YAxis
              tickLine={false}
              axisLine={false}
              tick={{ fill: "var(--color-ink-3)", fontSize: 10.5, fontFamily: "var(--font-mono)" }}
            />
            <Tooltip cursor={{ fill: "rgba(23, 34, 46, 0.05)" }} content={<RatioTooltip />} />
            <Bar dataKey="value" radius={[3, 3, 0, 0]} maxBarSize={56}>
              <LabelList
                dataKey="value"
                position="top"
                formatter={(value: ReactNode) => Number(value).toLocaleString("en-US")}
                style={{ fill: "var(--color-ink-2)", fontSize: 11, fontFamily: "var(--font-mono)" }}
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    );
  }

  return (
    <WidgetFrame
      title={widget.title}
      busy={loading}
      meta={loading || error ? undefined : `${rows.length} rows`}
    >
      {body}
    </WidgetFrame>
  );
}
