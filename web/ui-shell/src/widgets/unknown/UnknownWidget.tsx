import type { WidgetProps } from "../types";
import { WidgetFrame } from "../WidgetFrame";

export function UnknownWidget({ widget }: Readonly<WidgetProps>) {
  return (
    <WidgetFrame title={widget.title}>
      <div className="flex items-start gap-2.5 py-2">
        <span className="block text-[13px] font-medium text-ink-2">
          Widget type{" "}
          <code className="rounded border border-line-soft bg-surface-2 px-1.5 py-px font-mono text-xs text-ink">
            {widget.type}
          </code>{" "}
          is not supported by this shell.
        </span>
      </div>
    </WidgetFrame>
  );
}
