import axios, { AxiosError } from "axios";

export const api = axios.create({
  baseURL: "/api",
  // Send cookies so Spring's XSRF-TOKEN cookie is round-tripped; axios reads it
  // from `XSRF-TOKEN` and echoes it back as `X-XSRF-TOKEN` on mutating requests.
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

// Prime the XSRF-TOKEN cookie on app load so the first mutating request has a token to echo.
// Spring emits the cookie on any response because we eagerly load CSRF on the server side.
// We bypass the `/api` baseURL here because actuator is served directly under the backend root.
export async function primeCsrf(): Promise<void> {
  try {
    await axios.get("/actuator/health", { withCredentials: true });
  } catch {
    // non-fatal: login/register paths are CSRF-exempt; the response from those endpoints
    // still emits XSRF-TOKEN so subsequent mutating calls are covered either way.
  }
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (err: unknown) => {
    if (err instanceof AxiosError && err.response?.status === 401) {
      localStorage.removeItem("token");
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(err instanceof Error ? err : new Error(String(err)));
  },
);

export interface Account {
  id: number;
  accountType: "CHECKING" | "SAVINGS" | "CREDIT";
  balance: string;
  currency: string;
}

export interface AuthResponse {
  token: string;
  username: string;
  role: string;
}
