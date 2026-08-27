import type {ReactNode} from "react";
import type {WidgetProps} from "../types";
import {useWidgetData} from "../useWidgetData";
import {EmptyState, ErrorState, Skeleton, WidgetFrame} from "../WidgetFrame";

function columnsOf(rows: unknown[]): string[] {
    const first = rows.find((row) => typeof row === "object" && row !== null);
    if (!first) {
        return [];
    }
    // Backend JSON key order is serializer-dependent and varies across
    // restarts; sort deterministically so renders are stable.
    return Object.keys(first as Record<string, unknown>).sort((left, right) =>
        left.localeCompare(right, "en", {sensitivity: "base"}),
    );
}

function cellValue(row: unknown, column: string): unknown {
    if (typeof row !== "object" || row === null) {
        return undefined;
    }
    return (row as Record<string, unknown>)[column];
}

/** A column is numeric when every non-nullish cell is a number. */
function isNumericColumn(rows: unknown[], column: string): boolean {
    const values = rows
        .map((row) => cellValue(row, column))
        .filter((value) => value !== null && value !== undefined);
    return values.length > 0 && values.every((value) => typeof value === "number");
}

/**
 * Objects render as JSON so no `[object Object]` ever reaches the DOM;
 * blank cells return the empty string and render as an em dash.
 */
function formatCellValue(value: unknown): string {
    if (value === null || value === undefined || value === "") {
        return "";
    }
    if (typeof value === "object") {
        return JSON.stringify(value);
    }
    return String(value);
}

/**
 * Presentation vocabulary: well-known operational status tokens render as
 * chips; everything else stays plain text. Purely presentational — the
 * data itself is never rewritten.
 */
const CHIP_TONES: Record<string, string> = {
    CRITICAL: "border-danger/35 bg-danger/8 text-danger",
    ERROR: "border-accent/40 bg-accent/9 text-accent-deep",
    FAILED: "border-accent/40 bg-accent/9 text-accent-deep",
    WARNING: "border-warn/35 bg-warn/9 text-warn",
    WARN: "border-warn/35 bg-warn/9 text-warn",
    PARTIAL: "border-warn/35 bg-warn/9 text-warn",
    PENDING: "border-warn/35 bg-warn/9 text-warn",
    INFO: "border-chart-hi/35 bg-chart-hi/8 text-chart-hi",
    COMPLETE: "border-ok/35 bg-ok/9 text-ok",
    OK: "border-ok/35 bg-ok/9 text-ok",
};

const CHIP_BASE =
    "inline-block rounded border px-2 py-0.5 font-mono text-[10px] font-semibold uppercase leading-[1.6] tracking-[0.05em]";

function CellValue({value}: Readonly<{ value: unknown }>) {
    const text = formatCellValue(value);
    if (text === "") {
        return <span className="text-ink-3">—</span>;
    }
    const tone = CHIP_TONES[text.toUpperCase()];
    if (tone !== undefined && text.length <= 24) {
        return <span className={`${CHIP_BASE} ${tone}`}>{text.toLowerCase()}</span>;
    }
    return <>{text}</>;
}

export function DataTable({widget}: Readonly<WidgetProps>) {
    const {rows, error, loading} = useWidgetData(widget.path);
    const columns = columnsOf(rows);
    const numeric = new Set(columns.filter((column) => isNumericColumn(rows, column)));

    let body: ReactNode;
    if (loading) {
        body = <Skeleton kind="table"/>;
    } else if (error) {
        body = <ErrorState message={error}/>;
    } else if (rows.length === 0) {
        body = <EmptyState/>;
    } else {
        body = (
            <div
                className="mt-0.5 -mx-5 -mb-[18px] max-h-[336px] overflow-auto [scrollbar-color:var(--color-line-strong)_transparent] [scrollbar-width:thin]">
                <table className="w-full border-collapse text-[12.5px]">
                    <thead>
                    <tr>
                        {columns.map((column) => (
                            <th
                                key={column}
                                className={`sticky top-0 z-1 whitespace-nowrap border-b border-line bg-surface-2 px-3 py-2 text-left font-mono text-[10px] font-semibold uppercase tracking-[0.07em] text-ink-3 first:pl-5 last:pr-5 ${
                                    numeric.has(column) ? "text-right" : ""
                                }`}
                            >
                                {column}
                            </th>
                        ))}
                    </tr>
                    </thead>
                    <tbody>
                    {rows.map((row) => (
                        <tr
                            key={JSON.stringify(row)}
                            className="border-b border-line-soft last:border-b-0 hover:bg-surface-hover"
                        >
                            {columns.map((column) => (
                                <td
                                    key={column}
                                    className={`px-3 py-[9px] align-top text-ink first:pl-5 last:pr-5 ${
                                        numeric.has(column)
                                            ? "text-right font-mono text-[12px] whitespace-nowrap tabular-nums"
                                            : ""
                                    }`}
                                >
                                    <CellValue value={cellValue(row, column)}/>
                                </td>
                            ))}
                        </tr>
                    ))}
                    </tbody>
                </table>
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
