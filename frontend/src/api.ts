import axios, { AxiosError } from "axios";
import toast from "react-hot-toast";

export const api = axios.create({
  baseURL: "/api",
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

export async function primeCsrf(): Promise<void> {
  try {
    await axios.get("/actuator/health", { withCredentials: true });
  } catch {
    // non-fatal
  }
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers["X-Correlation-Id"] = crypto.randomUUID();
  return config;
});

function extractCorrelationId(err: AxiosError): string | null {
  const raw: unknown = err.response?.headers["x-correlation-id"];
  return typeof raw === "string" && raw.length > 0 ? raw : null;
}

function extractErrorMessage(err: AxiosError): string {
  const data: unknown = err.response?.data;
  if (data != null && typeof data === "object" && "message" in data) {
    const msg: unknown = (data as Record<string, unknown>).message;
    if (typeof msg === "string") {
      return msg;
    }
  }
  return err.message;
}

api.interceptors.response.use(
  (r) => r,
  (err: unknown) => {
    if (err instanceof AxiosError) {
      const correlationId = extractCorrelationId(err);
      const refTag = correlationId ? correlationId.slice(0, 8) : null;

      if (err.response?.status === 401) {
        localStorage.removeItem("token");
        localStorage.removeItem("username");
        localStorage.removeItem("role");
        if (window.location.pathname !== "/login") {
          const msg = refTag
            ? `Session expired, please log in again (ref: ${refTag})`
            : "Session expired, please log in again";
          toast.error(msg);
          window.location.href = "/login";
        }
      } else if (refTag && correlationId) {
        const detail = extractErrorMessage(err);
        toast.error(`${detail} (ref: ${refTag})`, {
          id: `err-${correlationId}`,
          duration: 6000,
        });
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
