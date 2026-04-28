import { useState } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { api } from "../../api";
import Spinner from "../../components/Spinner";

interface AuditEntry {
  id: number;
  occurredAt: string;
  eventType: string;
  outcome: string;
  actorUsername: string | null;
  resourceType: string | null;
  resourceId: string | null;
  payload: string;
  entryHash: string;
  prevHash: string | null;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface IntegrityResult {
  ok: boolean;
  brokenAtId: number | null;
  totalEntries: number;
}

export default function AdminAuditPage() {
  const [searchParams] = useSearchParams();
  const [eventType, setEventType] = useState("");
  const [outcome, setOutcome] = useState("");
  const [actorUsername, setActorUsername] = useState("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const showIntegrity = searchParams.get("integrity") === "1";

  const { data, isLoading, error } = useQuery<PageResponse<AuditEntry>>({
    queryKey: ["admin-audit", eventType, outcome, actorUsername, page],
    queryFn: async () => {
      const params: Record<string, string | number> = { page, size: pageSize };
      if (eventType) params.eventType = eventType;
      if (outcome) params.outcome = outcome;
      if (actorUsername) params.actorUsername = actorUsername;
      return (await api.get<PageResponse<AuditEntry>>("/admin/audit", { params })).data;
    },
  });

  const integrityMutation = useMutation({
    mutationFn: async () => (await api.get<IntegrityResult>("/admin/audit/integrity")).data,
  });

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between mb-4 gap-2">
        <h2 className="text-2xl font-semibold">Audit Log</h2>
        <button
          type="button"
          onClick={() => {
            integrityMutation.mutate();
          }}
          disabled={integrityMutation.isPending}
          className="px-4 py-2 bg-slate-800 text-white rounded text-sm hover:bg-slate-700 disabled:opacity-50"
        >
          {integrityMutation.isPending ? "Checking…" : "Verify chain integrity"}
        </button>
      </div>

      {(integrityMutation.data ?? (showIntegrity ? undefined : null)) !== null &&
        integrityMutation.data != null && (
          <div
            className={`mb-4 p-3 rounded text-sm ${
              integrityMutation.data.ok
                ? "bg-green-100 text-green-800 border border-green-300"
                : "bg-red-100 text-red-800 border border-red-300"
            }`}
          >
            {integrityMutation.data.ok
              ? `Chain is valid. ${String(integrityMutation.data.totalEntries)} entries verified.`
              : `Chain broken at entry #${String(integrityMutation.data.brokenAtId)}. ${String(integrityMutation.data.totalEntries)} entries total.`}
          </div>
        )}

      <div className="flex gap-3 mb-4 flex-wrap">
        <label className="text-sm">
          <span className="sr-only">Event type</span>
          <input
            type="text"
            placeholder="Event type"
            className="border rounded px-3 py-1.5 text-sm"
            value={eventType}
            onChange={(e) => {
              setEventType(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="text-sm">
          <span className="sr-only">Outcome</span>
          <input
            type="text"
            placeholder="Outcome"
            className="border rounded px-3 py-1.5 text-sm"
            value={outcome}
            onChange={(e) => {
              setOutcome(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="text-sm">
          <span className="sr-only">Actor username</span>
          <input
            type="text"
            placeholder="Actor username"
            className="border rounded px-3 py-1.5 text-sm"
            value={actorUsername}
            onChange={(e) => {
              setActorUsername(e.target.value);
              setPage(0);
            }}
          />
        </label>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-slate-500">
          <Spinner /> Loading audit log…
        </div>
      )}
      {error instanceof Error && <p className="text-red-600">Failed to load audit log.</p>}
      {(data?.content.length ?? 0) === 0 && data != null && (
        <p className="text-slate-500">No audit entries found.</p>
      )}
      {(data?.content.length ?? 0) > 0 && data != null && (
        <>
          <div className="bg-white rounded-lg shadow overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-100">
                <tr>
                  <th scope="col" className="text-left px-3 py-2">ID</th>
                  <th scope="col" className="text-left px-3 py-2">Time</th>
                  <th scope="col" className="text-left px-3 py-2">Event</th>
                  <th scope="col" className="text-left px-3 py-2">Outcome</th>
                  <th scope="col" className="text-left px-3 py-2">Actor</th>
                  <th scope="col" className="text-left px-3 py-2">Resource</th>
                  <th scope="col" className="text-left px-3 py-2">Hash</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((e) => (
                  <tr key={e.id} className="border-t hover:bg-slate-50">
                    <td className="px-3 py-2">{e.id}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {new Date(e.occurredAt).toLocaleString()}
                    </td>
                    <td className="px-3 py-2">{e.eventType}</td>
                    <td className="px-3 py-2">
                      <span
                        className={`px-2 py-0.5 rounded text-xs ${
                          e.outcome === "SUCCESS"
                            ? "bg-green-100 text-green-800"
                            : "bg-red-100 text-red-800"
                        }`}
                      >
                        {e.outcome}
                      </span>
                    </td>
                    <td className="px-3 py-2">{e.actorUsername ?? "—"}</td>
                    <td className="px-3 py-2">
                      {e.resourceType ? `${e.resourceType}/${e.resourceId ?? ""}` : "—"}
                    </td>
                    <td className="px-3 py-2 font-mono text-xs truncate max-w-[120px]" title={e.entryHash}>
                      {e.entryHash.slice(0, 12)}…
                    </td>
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
