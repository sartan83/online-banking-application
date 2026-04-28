import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, Account } from "../api";
import TransferForm from "../components/TransferForm";
import TransferHistory from "../components/TransferHistory";

export default function DashboardPage() {
  const navigate = useNavigate();
  const username = localStorage.getItem("username") ?? "";

  const { data, isLoading, error } = useQuery<Account[]>({
    queryKey: ["accounts"],
    queryFn: async () => (await api.get<Account[]>("/accounts")).data,
  });

  function signOut() {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    navigate("/login");
  }

  return (
    <div className="min-h-screen">
      <header className="bg-white border-b px-6 py-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold">DevilsVault</h1>
        <div className="flex items-center gap-4 text-sm">
          <span>{username}</span>
          <button onClick={signOut} className="underline">Sign out</button>
        </div>
      </header>
      <main className="p-6 max-w-4xl mx-auto space-y-6">
        <section>
          <h2 className="text-lg font-semibold mb-3">Your accounts</h2>
          {isLoading && <p>Loading…</p>}
          {error && <p className="text-red-600">Failed to load accounts.</p>}
          {data?.length === 0 && <p className="text-slate-600">No accounts yet.</p>}
          <ul className="grid gap-3 md:grid-cols-2">
            {data?.map((a) => (
              <li key={a.id} className="bg-white rounded-lg shadow p-4 flex items-center justify-between">
                <div>
                  <div className="text-sm text-slate-500">{a.accountType}</div>
                  <div className="text-xs text-slate-400">#{a.id}</div>
                </div>
                <div className="text-xl font-semibold">
                  {a.currency} {Number(a.balance).toFixed(2)}
                </div>
              </li>
            ))}
          </ul>
        </section>

        {data && data.length > 0 && (
          <section>
            <TransferForm accounts={data} />
          </section>
        )}

        <section>
          <TransferHistory />
        </section>
      </main>
    </div>
  );
}
