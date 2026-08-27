import type {CSSProperties} from "react";
import {GENERATION_ID, WIDGETS} from "./manifest";
import type {WidgetDescriptor} from "./widgets/types";
import {lookupRenderer, slotRank, slotSpanClass} from "./widgets/registry";
import {UnknownWidget} from "./widgets/unknown/UnknownWidget";
import {WidgetErrorBoundary} from "./widgets/ErrorBoundary";

/** Presentation order: headline numbers → trends → row-level detail. */
const orderedWidgets = [...WIDGETS].sort(
    (left, right) => slotRank(left.type) - slotRank(right.type),
);

function render(widget: WidgetDescriptor, index: number) {
    const Renderer = lookupRenderer(widget.type) ?? UnknownWidget;
    const style: CSSProperties = {animationDelay: `${index * 65}ms`};
    return (
        <div
            key={widget.id}
            className={`animate-card-in ${slotSpanClass(widget.type)}`}
            style={style}
        >
            <WidgetErrorBoundary title={widget.title}>
                <Renderer widget={widget}/>
            </WidgetErrorBoundary>
        </div>
    );
}

export default function App() {
    return (
        <div
            className="mx-auto flex min-h-screen max-w-[1280px] flex-col px-[clamp(20px,3.5vw,44px)] pt-[30px] pb-[18px]">
            <header className="flex flex-wrap items-start justify-between gap-4">
                <div className="flex items-center gap-3.5">
          <span
              aria-hidden="true"
              className="grid size-9 shrink-0 place-items-center rounded-[9px] bg-accent text-white shadow-[0_2px_6px_rgba(217,84,30,0.28)]"
          >
            <svg viewBox="0 0 20 20" className="size-4.5">
              <path
                  d="M4 6.5h12M4 10h8.5M4 13.5h5"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
              />
            </svg>
          </span>
                    <div>
                        <h1 className="text-[19px] leading-[1.2] font-semibold tracking-[-0.01em]">
                            Supply-Chain Operations
                        </h1>
                        <p className="mt-0.75 font-mono text-[11px] tracking-[0.02em] text-ink-3">
                            heron-forge · configuration-driven surface
                        </p>
                    </div>
                </div>
                <span
                    title={`Generation ${GENERATION_ID} — SHA-256 of the sealed definition`}
                    className="inline-flex cursor-default items-center gap-2 rounded-full border border-line bg-surface px-3 py-[7px] font-mono text-[11px] shadow-card"
                >
          <span className="size-[7px] shrink-0 animate-pulse-ring rounded-full bg-ok"/>
          <span className="text-[10px] font-semibold uppercase tracking-[0.1em] text-ink-2">
            gen
          </span>
          <span>{shorten(GENERATION_ID)}</span>
        </span>
            </header>

            <main className="mt-6.5 mb-7 grid grid-cols-12 items-stretch gap-3.5">
                {orderedWidgets.map((orderedWidget, index) => render(orderedWidget, index))}
            </main>

            <footer
                className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t border-line pt-3.5 font-mono text-[11px] text-ink-3">
        <span title={`Generation ${GENERATION_ID}`}>
          <span className="mr-[7px] inline-block size-1.5 animate-pulse-ring rounded-full bg-ok align-[1px]"/>
          generation <code className="font-mono text-ink-2">{shorten(GENERATION_ID, 16)}</code> ·
          sha-256 sealed
        </span>
                <span>heron-forge platform · live data</span>
            </footer>
        </div>
    );
}

function shorten(id: string, length = 10) {
    return id.length > length ? `${id.slice(0, length)}…` : id;
}
