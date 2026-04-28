import { Component } from "react";
import type { ErrorInfo, ReactNode } from "react";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("Uncaught error:", error, info);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center">
          <div className="text-center space-y-4">
            <h1 className="text-2xl font-semibold text-slate-800">
              Something went wrong
            </h1>
            <p className="text-slate-500">
              An unexpected error occurred. Please try reloading the page.
            </p>
            <button
              type="button"
              onClick={() => {
                window.location.reload();
              }}
              className="px-4 py-2 bg-slate-900 text-white rounded font-medium hover:bg-slate-800"
            >
              Reload
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
