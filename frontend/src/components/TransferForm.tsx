import { FormEvent, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AxiosError } from "axios";
import toast from "react-hot-toast";
import { api, Account } from "../api";

interface TransferFormProps {
  accounts: Account[];
}

interface ApiErrorBody {
  message?: string;
}

export default function TransferForm({ accounts }: TransferFormProps) {
  const queryClient = useQueryClient();
  const [sourceAccountId, setSourceAccountId] = useState("");
  const [targetAccountId, setTargetAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [memo, setMemo] = useState("");

  const mutation = useMutation({
    mutationFn: async (payload: {
      sourceAccountId: number;
      targetAccountId: number;
      amount: string;
      description: string;
    }) => {
      const res = await api.post<{ id: number }>("/transfers", payload);
      return res.data;
    },
    onSuccess: () => {
      toast.success("Transfer completed successfully.");
      setSourceAccountId("");
      setTargetAccountId("");
      setAmount("");
      setMemo("");
      void queryClient.invalidateQueries({ queryKey: ["accounts"] });
      void queryClient.invalidateQueries({ queryKey: ["transfers"] });
    },
    onError: (err: Error) => {
      const message =
        err instanceof AxiosError
          ? (err.response?.data as ApiErrorBody | undefined)?.message ??
            "Transfer failed"
          : "Transfer failed";
      toast.error(message);
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();

    if (!sourceAccountId) {
      toast.error("Please select a source account.");
      return;
    }
    if (!targetAccountId || isNaN(Number(targetAccountId))) {
      toast.error("Please enter a valid target account ID.");
      return;
    }
    const parsed = parseFloat(amount);
    if (isNaN(parsed) || parsed < 0.01) {
      toast.error("Amount must be at least 0.01.");
      return;
    }
    if (memo.length > 140) {
      toast.error("Memo must be 140 characters or fewer.");
      return;
    }

    mutation.mutate({
      sourceAccountId: Number(sourceAccountId),
      targetAccountId: Number(targetAccountId),
      amount: parsed.toFixed(2),
      description: memo,
    });
  }

  return (
    <form
      onSubmit={(e) => {
        onSubmit(e);
      }}
      className="bg-white rounded-lg shadow p-6 space-y-4"
    >
      <h3 className="text-lg font-semibold">New Transfer</h3>

      <label className="block text-sm">
        Source account
        <select
          className="mt-1 block w-full border rounded px-3 py-2"
          value={sourceAccountId}
          onChange={(e) => {
            setSourceAccountId(e.target.value);
          }}
          required
        >
          <option value="">Select an account</option>
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              #{a.id} — {a.accountType} ({a.currency} {Number(a.balance).toFixed(2)})
            </option>
          ))}
        </select>
      </label>

      <label className="block text-sm">
        Target account ID
        <input
          type="number"
          min="1"
          step="1"
          className="mt-1 block w-full border rounded px-3 py-2"
          value={targetAccountId}
          onChange={(e) => {
            setTargetAccountId(e.target.value);
          }}
          required
        />
      </label>

      <label className="block text-sm">
        Amount
        <input
          type="number"
          min="0.01"
          step="0.01"
          className="mt-1 block w-full border rounded px-3 py-2"
          value={amount}
          onChange={(e) => {
            setAmount(e.target.value);
          }}
          required
        />
      </label>

      <label className="block text-sm">
        Memo (optional, max 140 chars)
        <input
          type="text"
          maxLength={140}
          className="mt-1 block w-full border rounded px-3 py-2"
          value={memo}
          onChange={(e) => {
            setMemo(e.target.value);
          }}
        />
      </label>

      <button
        type="submit"
        disabled={mutation.isPending}
        className="w-full bg-slate-900 text-white rounded py-2 font-medium disabled:opacity-50"
      >
        {mutation.isPending ? "Sending…" : "Send Transfer"}
      </button>
    </form>
  );
}
