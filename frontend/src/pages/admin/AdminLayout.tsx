import type { JSX } from "react";
import { Navigate, Outlet, Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { useIsAdmin } from "../../hooks/useIsAdmin";
import { useIdleTimeout } from "../../hooks/useIdleTimeout";

function AdminNavLink({ to, children }: { to: string; children: React.ReactNode }) {
  const location = useLocation();
  const active = location.pathname === to;
  return (
    <Link
      to={to}
      className={`px-3 py-1 rounded text-sm ${active ? "bg-slate-800 text-white" : "text-slate-300 hover:text-white"}`}
    >
      {children}
    </Link>
  );
}

export default function AdminLayout(): JSX.Element {
  const { isAdmin, isLoading } = useIsAdmin();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const username = localStorage.getItem("username") ?? "";

  useIdleTimeout(() => {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    localStorage.removeItem("role");
    queryClient.clear();
    toast("Session expired due to inactivity", { icon: "⏰" });
    navigate("/login");
  });

  if (isLoading) {
    return <div className="min-h-screen flex items-center justify-center">Loading…</div>;
  }

  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }

  function signOut() {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    localStorage.removeItem("role");
    queryClient.clear();
    navigate("/login");
  }

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-1 rounded text-sm ${isActive ? "bg-slate-200 text-slate-900" : "text-slate-600 hover:text-slate-900"}`;

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-50 bg-white border-b px-4 sm:px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/" className="text-xl font-semibold whitespace-nowrap">
            DevilsVault
          </Link>
          <nav className="hidden sm:flex gap-1" aria-label="Main navigation">
            <NavLink to="/" end className={linkClass}>
              Dashboard
            </NavLink>
            <NavLink
              to="/admin"
              className={({ isActive }) =>
                `px-3 py-1 rounded text-sm ${isActive ? "bg-slate-800 text-white" : "text-slate-600 hover:text-slate-900"}`
              }
            >
              Admin
            </NavLink>
          </nav>
        </div>
        <div className="flex items-center gap-3 text-sm">
          <span className="hidden sm:inline text-slate-600">{username}</span>
          <button
            type="button"
            onClick={signOut}
            className="underline text-slate-600 hover:text-slate-900"
          >
            Sign out
          </button>
        </div>
      </header>

      <nav className="bg-slate-900 text-white px-4 sm:px-6 py-2 flex items-center gap-2" aria-label="Admin navigation">
        <AdminNavLink to="/admin">Dashboard</AdminNavLink>
        <AdminNavLink to="/admin/users">Users</AdminNavLink>
        <AdminNavLink to="/admin/audit">Audit Log</AdminNavLink>
        <Link to="/" className="ml-auto text-sm text-slate-300 hover:text-white underline">
          Back to app
        </Link>
      </nav>

      <main className="p-4 sm:p-6 max-w-6xl mx-auto">
        <Outlet />
      </main>
    </div>
  );
}
