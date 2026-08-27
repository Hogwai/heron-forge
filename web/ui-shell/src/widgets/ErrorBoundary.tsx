import {Component} from "react";
import type {ErrorInfo, ReactNode} from "react";
import {ErrorState, WidgetFrame} from "./WidgetFrame";

interface BoundaryProps {
    title: string;
    children: ReactNode;
}

interface BoundaryState {
    failure: string | null;
}

/**
 * Per-widget isolation: a render crash in one capability must never take
 * down the dashboard. Renders the failure inside the standard card chrome.
 */
export class WidgetErrorBoundary extends Component<BoundaryProps, BoundaryState> {
    state: BoundaryState = {failure: null};

    static getDerivedStateFromError(failure: unknown): BoundaryState {
        return {failure: failure instanceof Error ? failure.message : String(failure)};
    }

    componentDidCatch(error: Error, info: ErrorInfo) {
        console.error("[CAPTURED] widget render failure:", error, info.componentStack);
    }

    render() {
        const {failure} = this.state;
        if (failure !== null) {
            return (
                <WidgetFrame title={this.props.title}>
                    <ErrorState message={failure}/>
                </WidgetFrame>
            );
        }
        return this.props.children;
    }
}
