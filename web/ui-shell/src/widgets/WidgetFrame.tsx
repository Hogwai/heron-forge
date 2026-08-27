import type {CSSProperties, ReactNode} from "react";

function cx(...classes: Array<string | false | undefined>): string {
    return classes.filter(Boolean).join(" ");
}

/** The animated shimmer sweep shared by every skeleton bar. */
function Shimmer() {
    return (
        <span
            aria-hidden="true"
            className="absolute inset-0 -translate-x-full animate-shimmer bg-linear-to-r from-transparent via-white/70 to-transparent"
        />
    );
}

/**
 * Shared card chrome for every widget: surface, overline header with a
 * right-aligned meta slot, and the designed loading / error / empty bodies.
 * Renderers own their data hook; the frame owns presentation.
 *
 * `fill` lets a short card (KPI) stretch to its row-mates' height and center
 * its body instead of floating top-left.
 */
export function WidgetFrame({
                                title,
                                meta,
                                busy = false,
                                fill = false,
                                children,
                            }: Readonly<{
    title: string;
    meta?: ReactNode;
    busy?: boolean;
    fill?: boolean;
    children: ReactNode;
}>) {
    return (
        <article
            aria-busy={busy || undefined}
            className={cx(
                "h-full rounded-[10px] border border-line bg-surface px-5 py-[18px] shadow-card",
                "transition-[border-color,box-shadow] duration-180",
                "hover:border-line-strong hover:shadow-card-hover",
                fill && "flex flex-col",
            )}
        >
            <header className="mb-3.5 flex items-center justify-between gap-3">
                <h2 className="text-[11px] font-semibold uppercase tracking-[0.09em] text-ink-2">
                    {title}
                </h2>
                {meta !== undefined && (
                    <span className="whitespace-nowrap font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">
            {meta}
          </span>
                )}
            </header>
            <div className={cx(fill && "flex flex-1 flex-col justify-center")}>{children}</div>
        </article>
    );
}

export type SkeletonKind = "kpi" | "table" | "chart";

function SkeletonBar({
                         className,
                         style,
                     }: Readonly<{
    className?: string;
    style?: CSSProperties;
}>) {
    return (
        <span
            className={cx("relative block overflow-hidden rounded bg-surface-2", className)}
            style={style}
        >
      <Shimmer/>
    </span>
    );
}

export function Skeleton({kind}: Readonly<{ kind: SkeletonKind }>) {
    if (kind === "kpi") {
        return (
            <div className="flex flex-col gap-3 pt-1.5 pb-0.5" aria-hidden="true">
                <SkeletonBar className="h-[42px] w-22 rounded-md"/>
                <SkeletonBar className="h-2.5 w-32"/>
            </div>
        );
    }
    if (kind === "chart") {
        return (
            <div className="flex h-56 items-end gap-5 px-1 pt-2" aria-hidden="true">
                {[38, 62, 80, 52].map((height, index) => (
                    <span
                        key={index}
                        className="relative block max-w-16 flex-1 overflow-hidden rounded-t-sm bg-surface-2"
                        style={{height: `${height}%`}}
                    >
            <Shimmer/>
          </span>
                ))}
            </div>
        );
    }
    return (
        <div className="flex flex-col gap-[15px] py-1.5" aria-hidden="true">
            {["72%", "54%", "83%", "41%"].map((width, index) => (
                <SkeletonBar key={index} className="h-3" style={{width}}/>
            ))}
        </div>
    );
}

export function ErrorState({message}: Readonly<{ message: string }>) {
    return (
        <div className="flex items-start gap-2.5 py-2" role="alert">
            <svg className="mt-0.5 size-4 shrink-0 text-danger" viewBox="0 0 16 16" aria-hidden="true">
                <path
                    d="M8 1.5 15 14H1L8 1.5Z"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.4"
                    strokeLinejoin="round"
                />
                <path d="M8 6v4" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
                <circle cx="8" cy="12" r="0.9" fill="currentColor"/>
            </svg>
            <div>
                <span className="block text-[13px] font-semibold text-danger">Data unavailable</span>
                <span className="mt-0.75 block font-mono text-[11px] text-ink-3 [overflow-wrap:anywhere]">
          {message}
        </span>
            </div>
        </div>
    );
}

export function EmptyState() {
    return (
        <div className="flex items-start gap-2.5 py-2">
            <svg className="mt-0.5 size-4 shrink-0 text-ink-3" viewBox="0 0 16 16" aria-hidden="true">
                <path
                    d="M2 6.5 8 3l6 3.5V13H2V6.5Z"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.4"
                    strokeLinejoin="round"
                />
                <path d="M2 6.5 8 10l6-3.5" fill="none" stroke="currentColor" strokeWidth="1.4"/>
            </svg>
            <span className="block text-[13px] font-medium text-ink-2">
        No records in this generation
      </span>
        </div>
    );
}
