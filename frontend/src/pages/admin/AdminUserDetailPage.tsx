import { useParams, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api";

interface AdminAccount {
  id: number;
  type: string;
  balance: string;
  status: string;
}

interface AdminUserDetail {
  id: number;
  username: string;
  email: string;
  role: string;
  createdAt: string;
  accounts: AdminAccount[];
}

export default function AdminUserDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery<AdminUserDetail>({
    queryKey: ["admin-user", id],
    queryFn: async () => (await api.get<AdminUserDetail>(`/admin/users/${id ?? ""}`)).data,
    enabled: !!id,
  });

  const freezeMutation = useMutation({
    mutationFn: async (accountId: number) => {
      await api.post(`/admin/accounts/${String(accountId)}/freeze`);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-user", id] });
    },
  });

  const unfreezeMutation = useMutation({
    mutationFn: async (accountId: number) => {
      await api.post(`/admin/accounts/${String(accountId)}/unfreeze`);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-user", id] });
    },
  });

  if (isLoading) return <p>Loading…</p>;
  if (error) return <p className="text-red-600">Failed to load user.</p>;
  if (!data) return null;

  return (
    <div>
      <Link to="/admin/users" className="text-blue-600 underline text-sm">
        &larr; Back to users
      </Link>
      <h2 className="text-2xl font-semibold mt-2 mb-4">{data.username}</h2>
      <div className="bg-white rounded-lg shadow p-4 mb-6">
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-slate-500">Email</dt>
          <dd>{data.email}</dd>
          <dt className="text-slate-500">Role</dt>
          <dd>{data.role}</dd>
          <dt className="text-slate-500">Created</dt>
          <dd>{new Date(data.createdAt).toLocaleString()}</dd>
        </dl>
      </div>
      <h3 className="text-lg font-semibold mb-3">Accounts</h3>
      {data.accounts.length === 0 ? (
        <p className="text-slate-500">No accounts.</p>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-100">
              <tr>
                <th className="text-left px-4 py-2">ID</th>
                <th className="text-left px-4 py-2">Type</th>
                <th className="text-left px-4 py-2">Balance</th>
                <th className="text-left px-4 py-2">Status</th>
                <th className="text-left px-4 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.accounts.map((a) => (
                <tr key={a.id} className="border-t">
                  <td className="px-4 py-2">{a.id}</td>
                  <td className="px-4 py-2">{a.type}</td>
                  <td className="px-4 py-2">
                    ${Number(a.balance).toFixed(2)}
                  </td>
                  <td className="px-4 py-2">
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-medium ${
                        a.status === "FROZEN"
                          ? "bg-blue-100 text-blue-800"
                          : "bg-green-100 text-green-800"
                      }`}
                    >
                      {a.status}
                    </span>
                  </td>
                  <td className="px-4 py-2">
                    {a.status === "FROZEN" ? (
                      <button
                        onClick={() => {
                          unfreezeMutation.mutate(a.id);
                        }}
                        disabled={unfreezeMutation.isPending}
                        className="px-3 py-1 bg-green-600 text-white rounded text-xs hover:bg-green-700 disabled:opacity-50"
                      >
                        Unfreeze
                      </button>
                    ) : (
                      <button
                        onClick={() => {
                          freezeMutation.mutate(a.id);
                        }}
                        disabled={freezeMutation.isPending}
                        className="px-3 py-1 bg-red-600 text-white rounded text-xs hover:bg-red-700 disabled:opacity-50"
                      >
                        Freeze
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
