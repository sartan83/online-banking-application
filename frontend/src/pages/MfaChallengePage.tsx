import { FormEvent, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { api, LoginResponse } from "../api";

interface LocationState {
  partialToken: string;
  username: string;
}

export default function MfaChallengePage() {
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as LocationState | null;

  if (!state?.partialToken) {
    void Promise.resolve().then(() => {
      navigate("/login", { replace: true });
    });
    return null;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const { data } = await api.post<LoginResponse>("/auth/login/mfa", {
        partialToken: state?.partialToken,
        code,
      });
      if (data.accessToken) {
        localStorage.setItem("token", data.accessToken);
      }
      if (data.refreshToken) {
        localStorage.setItem("refreshToken", data.refreshToken);
      }
      if (state?.username) {
        localStorage.setItem("username", state.username);
      }
      navigate("/");
    } catch {
      setError("Invalid code. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <form
        onSubmit={(e) => {
          void onSubmit(e);
        }}
        className="bg-white p-8 rounded-lg shadow w-full max-w-sm space-y-4"
      >
        <h1 className="text-2xl font-semibold">Two-Factor Authentication</h1>
        <p className="text-sm text-slate-600">
          Enter the 6-digit code from your authenticator app, or a recovery code.
        </p>
        {error && <p className="text-red-600 text-sm">{error}</p>}
        <label className="block text-sm" htmlFor="mfa-code">
          Code
          <input
            id="mfa-code"
            className="mt-1 block w-full border rounded px-3 py-2 font-mono"
            value={code}
            onChange={(e) => {
              setCode(e.target.value);
            }}
            maxLength={10}
            required
            autoFocus
          />
        </label>
        <button
          className="w-full bg-slate-900 text-white rounded py-2 font-medium disabled:opacity-50"
          type="submit"
          disabled={code.length < 6 || submitting}
        >
          {submitting ? "Verifying..." : "Verify"}
        </button>
      </form>
    </div>
  );
}
