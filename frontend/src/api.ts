import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
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
  return config;
});

let isRefreshing = false;
let failedQueue: {
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
  config: InternalAxiosRequestConfig;
}[] = [];

function processQueue(error: Error | null): void {
  for (const prom of failedQueue) {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(api(prom.config));
    }
  }
  failedQueue = [];
}

api.interceptors.response.use(
  (r) => r,
  async (err: unknown) => {
    if (!(err instanceof AxiosError) || !err.config) {
      return Promise.reject(err instanceof Error ? err : new Error(String(err)));
    }

    const originalRequest = err.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (
      err.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes("/auth/refresh") &&
      !originalRequest.url?.includes("/auth/login")
    ) {
      const refreshToken = localStorage.getItem("refreshToken");
      if (refreshToken) {
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject, config: originalRequest });
          });
        }

        originalRequest._retry = true;
        isRefreshing = true;

        try {
          const { data } = await api.post<LoginResponse>("/auth/refresh", { refreshToken });
          localStorage.setItem("token", data.accessToken);
          localStorage.setItem("refreshToken", data.refreshToken);
          originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
          processQueue(null);
          return await api(originalRequest);
        } catch {
          processQueue(new Error("Refresh failed"));
          clearAuthStorage();
          if (window.location.pathname !== "/login") {
            toast.error("Session expired, please log in again");
            window.location.href = "/login";
          }
          return await Promise.reject(err);
        } finally {
          isRefreshing = false;
        }
      } else {
        clearAuthStorage();
        if (window.location.pathname !== "/login") {
          toast.error("Session expired, please log in again");
          window.location.href = "/login";
        }
      }
    }
    return Promise.reject(err instanceof Error ? err : new Error(String(err)));
  },
);

function clearAuthStorage(): void {
  localStorage.removeItem("token");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("username");
  localStorage.removeItem("role");
}

export { clearAuthStorage };

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

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}
