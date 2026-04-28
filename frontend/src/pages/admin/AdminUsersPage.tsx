import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../../api";
import Spinner from "../../components/Spinner";

interface AdminUser {
  id: number;
  username: string;
  email: string;
  role: string;
  createdAt: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export default function AdminUsersPage() {
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const { data, isLoading, error } = useQuery<PageResponse<AdminUser>>({
    queryKey: ["admin-users", search, page],
    queryFn: async () =>
      (
        await api.get<PageResponse<AdminUser>>("/admin/users", {
          params: { q: search, page, size: pageSize },
        })
      ).data,
  });

  return (
    <div>
      <h2 className="text-2xl font-semibold mb-4">Users</h2>
      <div className="mb-4">
        <label className="block text-sm">
          <span className="sr-only">Search users</span>
          <input
            type="text"
            placeholder="Search by username or email…"
            className="border rounded px-3 py-2 w-full max-w-md"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </label>
      </div>
      {isLoading && (
        <div className="flex items-center gap-2 text-slate-500">
          <Spinner /> Loading users…
        </div>
      )}
      {error instanceof Error && <p className="text-red-600">Failed to load users.</p>}
      {(data?.content.length ?? 0) === 0 && data != null && (
        <p className="text-slate-500">No users found.</p>
      )}
      {(data?.content.length ?? 0) > 0 && data != null && (
        <>
          <div className="bg-white rounded-lg shadow overflow-hidden overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-100">
                <tr>
                  <th scope="col" className="text-left px-4 py-2">ID</th>
                  <th scope="col" className="text-left px-4 py-2">Username</th>
                  <th scope="col" className="text-left px-4 py-2">Email</th>
                  <th scope="col" className="text-left px-4 py-2">Role</th>
                  <th scope="col" className="text-left px-4 py-2">Created</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((u) => (
                  <tr key={u.id} className="border-t hover:bg-slate-50">
                    <td className="px-4 py-2">{u.id}</td>
                    <td className="px-4 py-2">
                      <Link to={`/admin/users/${String(u.id)}`} className="text-blue-600 underline">
                        {u.username}
                      </Link>
                    </td>
                    <td className="px-4 py-2">{u.email}</td>
                    <td className="px-4 py-2">{u.role}</td>
                    <td className="px-4 py-2 whitespace-nowrap">{new Date(u.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex items-center gap-4 text-sm">
            <button
              type="button"
              onClick={() => {
                setPage((p) => Math.max(0, p - 1));
              }}
              disabled={page === 0}
              className="px-3 py-1 border rounded disabled:opacity-50"
            >
              Previous
            </button>
            <span>
              Page {data.number + 1} of {Math.max(data.totalPages, 1)}
            </span>
            <button
              type="button"
              onClick={() => {
                setPage((p) => p + 1);
              }}
              disabled={page + 1 >= data.totalPages}
              className="px-3 py-1 border rounded disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
}
