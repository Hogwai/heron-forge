import raw from "@generated/widgets.json";
import type { WidgetDescriptor } from "./widgets/types";

export interface WidgetManifest {
  applicationId: string;
  generationId: string;
  widgets: WidgetDescriptor[];
}

const MANIFEST = raw as WidgetManifest;

function isDescriptor(w: unknown): w is WidgetDescriptor {
  const candidate = w as Partial<WidgetDescriptor> | null;
  return (
    typeof candidate?.id === "string" &&
    typeof candidate?.type === "string" &&
    typeof candidate?.title === "string" &&
    typeof candidate?.path === "string"
  );
}

export const WIDGETS: WidgetDescriptor[] = Array.isArray(MANIFEST.widgets)
  ? MANIFEST.widgets.filter(isDescriptor)
  : [];

export const GENERATION_ID: string =
  typeof MANIFEST.generationId === "string" ? MANIFEST.generationId : "unknown";
