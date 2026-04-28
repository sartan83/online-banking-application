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
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (err: unknown) => {
    if (err instanceof AxiosError && err.response?.status === 401) {
      localStorage.removeItem("token");
      localStorage.removeItem("username");
      localStorage.removeItem("role");
      if (window.location.pathname !== "/login") {
        toast.error("Session expired, please log in again");
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
