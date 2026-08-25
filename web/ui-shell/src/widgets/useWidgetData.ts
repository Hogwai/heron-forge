import { useEffect, useState } from "react";

const cache = new Map<string, Promise<unknown[]>>();

function fetchRows(path: string): Promise<unknown[]> {
  let pending = cache.get(path);
  if (!pending) {
    pending = fetch(path).then(async (response) => {
      if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`);
      }
      const payload: unknown = await response.json();
      if (Array.isArray(payload)) {
        return payload;
      }
      if (
        payload !== null &&
        typeof payload === "object" &&
        Array.isArray((payload as { rows?: unknown }).rows)
      ) {
        return (payload as { rows: unknown[] }).rows;
      }
      throw new Error("unexpected payload shape");
    });
    cache.set(path, pending);
  }
  return pending;
}

interface WidgetData {
  rows: unknown[];
  error: string | null;
  loading: boolean;
}

export function useWidgetData(path: string): WidgetData {
  const [state, setState] = useState<WidgetData>({ rows: [], error: null, loading: true });
  useEffect(() => {
    let cancelled = false;
    setState({ rows: [], error: null, loading: true });
    fetchRows(path)
      .then((rows) => {
        if (!cancelled) setState({ rows, error: null, loading: false });
      })
      .catch((error_: unknown) => {
        if (!cancelled) setState({rows: [], error: String(error_), loading: false });
      });
    return () => {
      cancelled = true;
    };
  }, [path]);
  return state;
}
