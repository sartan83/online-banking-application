import { Link } from "react-router-dom";

const tiles = [
  { title: "Users", description: "Manage user accounts", to: "/admin/users" },
  { title: "Audit Log", description: "View audit event history", to: "/admin/audit" },
  { title: "Integrity", description: "Verify audit chain integrity", to: "/admin/audit?integrity=1" },
];

export default function AdminDashboardPage() {
  return (
    <div>
      <h2 className="text-2xl font-semibold mb-6">Admin Dashboard</h2>
      <div className="grid gap-4 md:grid-cols-3">
        {tiles.map((tile) => (
          <Link
            key={tile.title}
            to={tile.to}
            className="bg-white rounded-lg shadow p-6 hover:shadow-md transition-shadow"
          >
            <h3 className="text-lg font-semibold">{tile.title}</h3>
            <p className="text-slate-500 text-sm mt-1">{tile.description}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
