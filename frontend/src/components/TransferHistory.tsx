import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";

interface TransferItem {
  id: number;
  sourceAccountId: number;
  targetAccountId: number;
  amount: string;
  currency: string;
  memo: string | null;
  occurredAt: string;
  direction: "DEBIT" | "CREDIT";
}

interface TransferListResponse {
  items: TransferItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export default function TransferHistory() {
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const { data, isLoading, isError } = useQuery<TransferListResponse>({
    queryKey: ["transfers", page],
    queryFn: async () => {
      const res = await api.get<TransferListResponse>("/transfers", {
        params: { page, size: pageSize },
      });
      return res.data;
    },
  });

  return (
    <div className="bg-white rounded-lg shadow p-6 space-y-4">
      <h3 className="text-lg font-semibold">Transfer History</h3>

      {isLoading && <p className="text-slate-500">Loading transfers…</p>}
      {isError && (
        <p className="text-red-600">Failed to load transfers.</p>
      )}

      {data?.items.length === 0 && (
        <p className="text-slate-500">No transfers yet.</p>
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-slate-500">
                  <th className="pb-2 pr-4">Date</th>
                  <th className="pb-2 pr-4">Direction</th>
                  <th className="pb-2 pr-4">Counterpart</th>
                  <th className="pb-2 pr-4 text-right">Amount</th>
                  <th className="pb-2">Memo</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((t) => (
                  <tr key={t.id} className="border-b last:border-0">
                    <td className="py-2 pr-4 whitespace-nowrap">
                      {new Date(t.occurredAt).toLocaleDateString()}
                    </td>
                    <td className="py-2 pr-4">
                      {t.direction === "DEBIT" ? (
                        <span className="text-red-600" title="Outgoing">&#x2191; Out</span>
                      ) : (
                        <span className="text-green-600" title="Incoming">&#x2193; In</span>
                      )}
                    </td>
                    <td className="py-2 pr-4">
                      #{t.direction === "DEBIT" ? t.targetAccountId : t.sourceAccountId}
                    </td>
                    <td
                      className={`py-2 pr-4 text-right font-medium ${
                        t.direction === "DEBIT" ? "text-red-600" : "text-green-600"
                      }`}
                    >
                      {t.direction === "DEBIT" ? "-" : "+"}
                      {t.currency} {Number(t.amount).toFixed(2)}
                    </td>
                    <td className="py-2 text-slate-500 truncate max-w-[200px]">
                      {t.memo ?? "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="flex items-center justify-between pt-2">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => {
                  setPage((p) => Math.max(0, p - 1));
                }}
                className="text-sm underline disabled:opacity-40 disabled:no-underline"
              >
                Previous
              </button>
              <span className="text-sm text-slate-500">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <button
                type="button"
                disabled={page >= data.totalPages - 1}
                onClick={() => {
                  setPage((p) => p + 1);
                }}
                className="text-sm underline disabled:opacity-40 disabled:no-underline"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
