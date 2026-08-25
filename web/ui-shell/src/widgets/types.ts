export interface WidgetDescriptor {
  id: string;
  /**
   * Producer-validated discriminant (backend ALLOWED_TYPES). The shell stays
   * a lenient consumer: unregistered types fall back to the unknown widget.
   */
  type: string;
  title: string;
  path: string;
}

export interface WidgetProps {
  widget: WidgetDescriptor;
}
