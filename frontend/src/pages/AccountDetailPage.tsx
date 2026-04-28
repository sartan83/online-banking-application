import { useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import Spinner from "../components/Spinner";

interface AccountDetail {
  id: number;
  accountType: "CHECKING" | "SAVINGS" | "CREDIT";
  balance: string;
  currency: string;
  openedAt: string;
}

interface StatementEntry {
  id: number;
  occurredAt: string;
  direction: "DEBIT" | "CREDIT";
  amount: string;
  currency: string;
  counterpartAccountId: number;
  memo: string | null;
  runningBalance: string;
}

interface StatementPage {
  items: StatementEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export default function AccountDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [since, setSince] = useState("");
  const [until, setUntil] = useState("");
  const [direction, setDirection] = useState("");

  const accountQuery = useQuery<AccountDetail>({
    queryKey: ["account", id],
    queryFn: async () => (await api.get<AccountDetail>(`/accounts/${String(id)}`)).data,
  });

  const statementQuery = useQuery<StatementPage>({
    queryKey: ["statement", id, page, size, since, until, direction],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("size", String(size));
      if (since) {
        params.set("since", new Date(since).toISOString());
      }
      if (until) {
        params.set("until", new Date(until).toISOString());
      }
      if (direction) {
        params.set("direction", direction);
      }
      return (await api.get<StatementPage>(`/accounts/${String(id)}/statement?${params.toString()}`)).data;
    },
  });

  const account = accountQuery.data;
  const statement = statementQuery.data;

  return (
    <div className="space-y-6">
      <Link to="/" className="text-sm text-slate-500 hover:underline">&larr; Back to accounts</Link>

      {accountQuery.isLoading && (
        <div className="flex items-center gap-2 text-slate-500">
          <Spinner /> Loading account…
        </div>
      )}
      {accountQuery.error instanceof Error && (
        <p className="text-red-600">Failed to load account.</p>
      )}

      {account && (
        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex items-center justify-between flex-wrap gap-4">
            <div>
              <div className="text-sm text-slate-500">{account.accountType} #{account.id}</div>
              <div className="text-sm text-slate-400">
                Opened {new Date(account.openedAt).toLocaleDateString()}
              </div>
            </div>
            <div className="text-2xl font-semibold">
              {account.currency} {Number(account.balance).toFixed(2)}
            </div>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg shadow p-4">
        <h2 className="text-lg font-semibold mb-4">Statement</h2>

        <div className="flex flex-wrap gap-4 mb-4 items-end">
          <label className="block text-sm">
            Since
            <input
              type="date"
              className="mt-1 block border rounded px-3 py-2"
              value={since}
              onChange={(e) => { setSince(e.target.value); setPage(0); }}
            />
          </label>
          <label className="block text-sm">
            Until
            <input
              type="date"
              className="mt-1 block border rounded px-3 py-2"
              value={until}
              onChange={(e) => { setUntil(e.target.value); setPage(0); }}
            />
          </label>
          <label className="block text-sm">
            Direction
            <select
              className="mt-1 block border rounded px-3 py-2"
              value={direction}
              onChange={(e) => { setDirection(e.target.value); setPage(0); }}
            >
              <option value="">All</option>
              <option value="DEBIT">Debit</option>
              <option value="CREDIT">Credit</option>
            </select>
          </label>
        </div>

        {statementQuery.isLoading && (
          <div className="flex items-center gap-2 text-slate-500">
            <Spinner /> Loading statement…
          </div>
        )}
        {statementQuery.error instanceof Error && (
          <p className="text-red-600">Failed to load statement.</p>
        )}

        {statement?.items.length === 0 && (
          <p className="text-slate-500">No entries found.</p>
        )}

        {(statement?.items.length ?? 0) > 0 && statement && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-slate-500">
                    <th scope="col" className="py-2 pr-4">Date</th>
                    <th scope="col" className="py-2 pr-4">Direction</th>
                    <th scope="col" className="py-2 pr-4">Counterpart</th>
                    <th scope="col" className="py-2 pr-4 text-right">Amount</th>
                    <th scope="col" className="py-2 pr-4">Memo</th>
                    <th scope="col" className="py-2 text-right">Balance</th>
                  </tr>
                </thead>
                <tbody>
                  {statement.items.map((entry) => (
                    <tr key={entry.id} className="border-b hover:bg-slate-50">
                      <td className="py-2 pr-4 whitespace-nowrap">
                        {new Date(entry.occurredAt).toLocaleString()}
                      </td>
                      <td className="py-2 pr-4">
                        <span className={entry.direction === "DEBIT" ? "text-red-600" : "text-green-600"}>
                          {entry.direction === "DEBIT" ? "\u2193" : "\u2191"} {entry.direction}
                        </span>
                      </td>
                      <td className="py-2 pr-4">#{entry.counterpartAccountId}</td>
                      <td className={`py-2 pr-4 text-right font-mono ${entry.direction === "DEBIT" ? "text-red-600" : "text-green-600"}`}>
                        {entry.direction === "DEBIT" ? "-" : "+"}{Number(entry.amount).toFixed(2)} {entry.currency}
                      </td>
                      <td className="py-2 pr-4 text-slate-500">{entry.memo ?? ""}</td>
                      <td className="py-2 text-right font-mono">
                        {Number(entry.runningBalance).toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex items-center justify-between mt-4">
              <button
                type="button"
                className="px-3 py-1 border rounded disabled:opacity-50"
                disabled={page === 0}
                onClick={() => { setPage((p) => Math.max(0, p - 1)); }}
              >
                Prev
              </button>
              <span className="text-sm text-slate-500">
                Page {statement.page + 1} of {statement.totalPages}
              </span>
              <button
                type="button"
                className="px-3 py-1 border rounded disabled:opacity-50"
                disabled={page >= statement.totalPages - 1}
                onClick={() => { setPage((p) => p + 1); }}
              >
                Next
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
