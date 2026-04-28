import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, Account } from "../api";
import TransferForm from "../components/TransferForm";
import TransferHistory from "../components/TransferHistory";
import Spinner from "../components/Spinner";

export default function DashboardPage() {
  const { data, isLoading, error } = useQuery<Account[]>({
    queryKey: ["accounts"],
    queryFn: async () => (await api.get<Account[]>("/accounts")).data,
  });

  return (
    <div className="space-y-6">
      <section>
        <h2 className="text-lg font-semibold mb-3">Your accounts</h2>
        {isLoading && (
          <div className="flex items-center gap-2 text-slate-500">
            <Spinner /> Loading accounts…
          </div>
        )}
        {error instanceof Error && (
          <p className="text-red-600">Failed to load accounts.</p>
        )}
        {data?.length === 0 && (
          <p className="text-slate-500">No accounts yet.</p>
        )}
        <ul className="grid gap-3 sm:grid-cols-2">
          {data?.map((a) => (
            <li key={a.id}>
              <Link
                to={`/accounts/${String(a.id)}`}
                className="bg-white rounded-lg shadow p-4 flex items-center justify-between hover:ring-2 hover:ring-slate-300 transition block"
              >
                <div>
                  <div className="text-sm text-slate-500">{a.accountType}</div>
                  <div className="text-xs text-slate-400">#{a.id}</div>
                </div>
                <div className="text-xl font-semibold">
                  {a.currency} {Number(a.balance).toFixed(2)}
                </div>
              </Link>
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
    </div>
  );
}
