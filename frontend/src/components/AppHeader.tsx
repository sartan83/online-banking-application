import { Link, NavLink, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useIsAdmin } from "../hooks/useIsAdmin";
import { api, clearAuthStorage } from "../api";

export default function AppHeader() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const username = localStorage.getItem("username") ?? "";
  const { isAdmin } = useIsAdmin();

  function signOut() {
    const refreshToken = localStorage.getItem("refreshToken");
    if (refreshToken) {
      void api.post("/auth/logout", { refreshToken }).catch(() => {
        // best-effort: clear local state regardless
      });
    }
    clearAuthStorage();
    queryClient.clear();
    navigate("/login");
  }

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-1 rounded text-sm ${isActive ? "bg-slate-800 text-white" : "text-slate-600 hover:text-slate-900"}`;

  return (
    <header className="sticky top-0 z-50 bg-white border-b px-4 sm:px-6 py-3 flex items-center justify-between">
      <div className="flex items-center gap-4">
        <Link to="/" className="text-xl font-semibold whitespace-nowrap">
          DevilsVault
        </Link>
        <nav className="hidden sm:flex gap-1" aria-label="Main navigation">
          <NavLink to="/" end className={linkClass}>
            Dashboard
          </NavLink>
          {isAdmin && (
            <NavLink to="/admin" className={linkClass}>
              Admin
            </NavLink>
          )}
        </nav>
      </div>
      <div className="flex items-center gap-3 text-sm">
        <span className="hidden sm:inline text-slate-600">{username}</span>
        <NavLink to="/settings" className={linkClass}>
          Settings
        </NavLink>
        <button
          type="button"
          onClick={signOut}
          className="underline text-slate-600 hover:text-slate-900"
        >
          Sign out
        </button>
      </div>
    </header>
  );
}
