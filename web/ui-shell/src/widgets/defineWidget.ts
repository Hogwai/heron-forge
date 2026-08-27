import type {ComponentType} from "react";
import type {WidgetProps} from "./types";

/**
 * Presentation slot — decides the widget's grid span and ordering rank in
 * the shell. The closed set of placements the shell knows how to render.
 */
export type LayoutSlot = "compact" | "medium" | "wide";

export interface WidgetDefinition<T extends string = string> {
    /** Discriminant — by convention, the folder name. Registered at runtime. */
    type: T;
    /** Grid placement travels with the widget. */
    slot: LayoutSlot;
    /** Renderer for descriptors of this type. */
    component: ComponentType<WidgetProps>;
}

/**
 * Single registration point for a widget type: everything the shell needs
 * lives in one self-describing object. `T` is inferred as a string literal,
 * so a missing field or a malformed discriminant fails compilation inside
 * the widget's own folder — never in a central registry.
 */
export function defineWidget<T extends string>(
    definition: WidgetDefinition<T>,
): WidgetDefinition<T> {
    return definition;
}
