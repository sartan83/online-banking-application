import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { primeCsrf } from "./api";
import "./index.css";

const queryClient = new QueryClient();

void primeCsrf();

const rootEl = document.getElementById("root");
if (!rootEl) {
  throw new Error("Root element #root not found in index.html");
}
ReactDOM.createRoot(rootEl).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
);
