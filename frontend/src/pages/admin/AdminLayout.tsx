import type { JSX } from "react";
import { Navigate, Outlet, Link, useLocation } from "react-router-dom";
import { useIsAdmin } from "../../hooks/useIsAdmin";

function NavLink({ to, children }: { to: string; children: React.ReactNode }) {
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

  if (isLoading) {
    return <div className="min-h-screen flex items-center justify-center">Loading…</div>;
  }

  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-slate-900 text-white px-6 py-3 flex items-center gap-6">
        <Link to="/admin" className="text-lg font-semibold">
          Admin
        </Link>
        <nav className="flex gap-2">
          <NavLink to="/admin">Dashboard</NavLink>
          <NavLink to="/admin/users">Users</NavLink>
          <NavLink to="/admin/audit">Audit Log</NavLink>
        </nav>
        <div className="ml-auto">
          <Link to="/" className="text-sm text-slate-300 hover:text-white underline">
            Back to app
          </Link>
        </div>
      </header>
      <main className="p-6 max-w-6xl mx-auto">
        <Outlet />
      </main>
    </div>
  );
}
