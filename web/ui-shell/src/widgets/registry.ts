import type { ComponentType } from "react";
import type { WidgetProps } from "./types";
import type { LayoutSlot, WidgetDefinition } from "./defineWidget";

/**
 * Discovery by glob: every `widgets/<folder>/index.ts` that default-exports
 * a defineWidget(...) call registers itself here. Adding a widget type means
 * adding a folder — zero central edits. Root-level files (types.ts,
 * useWidgetData.ts, WidgetFrame.tsx, …) do not match the pattern.
 */
const modules = import.meta.glob<{ default: WidgetDefinition }>("./*/index.ts", {
  eager: true,
});
const DEFINITIONS = new Map<string, WidgetDefinition>();
for (const { default: definition } of Object.values(modules)) {
  if (DEFINITIONS.has(definition.type)) {
    console.warn(
      `[registry] duplicate widget type "${definition.type}" — keeping the first registration`,
    );
    continue;
  }
  DEFINITIONS.set(definition.type, definition);
}

/**
 * Renderer table built from the discovered definitions. Keyed by the
 * descriptor's `type` (a plain string — the backend contract is frozen and
 * producer-validated); consumers stay lenient.
 */
export const RENDERERS: Record<string, ComponentType<WidgetProps>> = Object.fromEntries(
  [...DEFINITIONS.entries()].map(([type, definition]) => [type, definition.component]),
);

/** Derived, not declared: the union of registered discriminants. */
export type WidgetType = keyof typeof RENDERERS;

/** Lenient renderer lookup — unregistered types return undefined. */
export function lookupRenderer(type: string): ComponentType<WidgetProps> | undefined {
  return DEFINITIONS.get(type)?.component;
}

const SLOT_SPAN_CLASS: Record<LayoutSlot, string> = {
  compact: "col-span-12 lg:col-span-6 xl:col-span-4",
  medium: "col-span-12 xl:col-span-8",
  wide: "col-span-12",
};

const SLOT_RANK: Record<LayoutSlot, number> = { compact: 0, medium: 1, wide: 2 };

/** Span classes for a type's slot; unregistered types render full-width. */
export function slotSpanClass(type: string): string {
  return SLOT_SPAN_CLASS[DEFINITIONS.get(type)?.slot ?? "wide"];
}

/** Ordering rank: headline numbers → trends → detail; unknown types last. */
export function slotRank(type: string): number {
  const slot = DEFINITIONS.get(type)?.slot;
  return slot === undefined ? Number.MAX_SAFE_INTEGER : SLOT_RANK[slot];
}
