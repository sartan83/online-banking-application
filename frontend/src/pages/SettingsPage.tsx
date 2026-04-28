import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import { api } from "../api";

interface MeResponse {
  username: string;
  role: string;
  mfaEnabled: boolean;
}

interface EnrollResponse {
  secret: string;
  otpauthUrl: string;
  recoveryCodes: string[];
}

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const [verifyCode, setVerifyCode] = useState("");
  const [disableCode, setDisableCode] = useState("");
  const [enrollData, setEnrollData] = useState<EnrollResponse | null>(null);
  const [showRecoveryCodes, setShowRecoveryCodes] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data: me, isLoading } = useQuery<MeResponse>({
    queryKey: ["me"],
    queryFn: async () => (await api.get<MeResponse>("/me")).data,
    staleTime: 30_000,
  });

  const enrollMutation = useMutation({
    mutationFn: async () => (await api.post<EnrollResponse>("/auth/mfa/enroll")).data,
    onSuccess: (data) => {
      setEnrollData(data);
      setShowRecoveryCodes(true);
      setError(null);
    },
    onError: () => {
      setError("Failed to start MFA enrollment");
    },
  });

  const verifyMutation = useMutation({
    mutationFn: async (code: string) =>
      (await api.post<{ mfaEnabled: boolean }>("/auth/mfa/verify", { code })).data,
    onSuccess: () => {
      setEnrollData(null);
      setShowRecoveryCodes(false);
      setVerifyCode("");
      setError(null);
      void queryClient.invalidateQueries({ queryKey: ["me"] });
    },
    onError: () => {
      setError("Invalid TOTP code. Please try again.");
    },
  });

  const disableMutation = useMutation({
    mutationFn: async (code: string) =>
      (await api.post<{ mfaEnabled: boolean }>("/auth/mfa/disable", { code })).data,
    onSuccess: () => {
      setDisableCode("");
      setError(null);
      void queryClient.invalidateQueries({ queryKey: ["me"] });
    },
    onError: () => {
      setError("Invalid code. Please try again.");
    },
  });

  if (isLoading) {
    return <div className="p-6">Loading...</div>;
  }

  return (
    <div className="max-w-2xl mx-auto p-6 space-y-6">
      <h1 className="text-2xl font-semibold">Settings</h1>

      <section className="bg-white rounded-lg shadow p-6 space-y-4">
        <h2 className="text-lg font-medium">Multi-Factor Authentication (MFA)</h2>
        <p className="text-sm text-slate-600">
          Status:{" "}
          <span className={me?.mfaEnabled ? "text-green-700 font-medium" : "text-slate-500"}>
            {me?.mfaEnabled ? "Enabled" : "Disabled"}
          </span>
        </p>

        {error && <p className="text-red-600 text-sm">{error}</p>}

        {!me?.mfaEnabled && !enrollData && (
          <button
            type="button"
            onClick={() => {
              enrollMutation.mutate();
            }}
            disabled={enrollMutation.isPending}
            className="bg-slate-900 text-white rounded px-4 py-2 text-sm font-medium hover:bg-slate-800 disabled:opacity-50"
          >
            {enrollMutation.isPending ? "Enrolling..." : "Enroll MFA"}
          </button>
        )}

        {enrollData && (
          <div className="space-y-4 border rounded-lg p-4 bg-slate-50">
            <p className="text-sm font-medium">
              Scan this QR code with your authenticator app:
            </p>
            <div className="flex justify-center">
              <QRCodeSVG value={enrollData.otpauthUrl} size={200} />
            </div>
            <p className="text-xs text-slate-500 break-all text-center">
              Manual entry key: <code className="font-mono">{enrollData.secret}</code>
            </p>

            {showRecoveryCodes && (
              <div className="bg-amber-50 border border-amber-200 rounded p-3 space-y-2">
                <p className="text-sm font-medium text-amber-800">
                  Save these recovery codes (shown only once):
                </p>
                <div className="grid grid-cols-2 gap-1">
                  {enrollData.recoveryCodes.map((code) => (
                    <code key={code} className="text-xs font-mono bg-white px-2 py-1 rounded border">
                      {code}
                    </code>
                  ))}
                </div>
              </div>
            )}

            <div className="space-y-2">
              <label className="block text-sm" htmlFor="verify-code">
                Enter the 6-digit code from your authenticator:
              </label>
              <div className="flex gap-2">
                <input
                  id="verify-code"
                  type="text"
                  maxLength={6}
                  className="border rounded px-3 py-2 w-32 font-mono"
                  value={verifyCode}
                  onChange={(e) => {
                    setVerifyCode(e.target.value);
                  }}
                  placeholder="000000"
                />
                <button
                  type="button"
                  onClick={() => {
                    verifyMutation.mutate(verifyCode);
                  }}
                  disabled={verifyCode.length !== 6 || verifyMutation.isPending}
                  className="bg-slate-900 text-white rounded px-4 py-2 text-sm font-medium hover:bg-slate-800 disabled:opacity-50"
                >
                  {verifyMutation.isPending ? "Verifying..." : "Verify"}
                </button>
              </div>
            </div>
          </div>
        )}

        {me?.mfaEnabled && (
          <div className="border rounded-lg p-4 space-y-2">
            <p className="text-sm">Enter a TOTP code or recovery code to disable MFA:</p>
            <div className="flex gap-2">
              <input
                type="text"
                maxLength={10}
                className="border rounded px-3 py-2 w-40 font-mono"
                value={disableCode}
                onChange={(e) => {
                  setDisableCode(e.target.value);
                }}
                placeholder="Code"
              />
              <button
                type="button"
                onClick={() => {
                  disableMutation.mutate(disableCode);
                }}
                disabled={disableCode.length < 6 || disableMutation.isPending}
                className="bg-red-600 text-white rounded px-4 py-2 text-sm font-medium hover:bg-red-700 disabled:opacity-50"
              >
                {disableMutation.isPending ? "Disabling..." : "Disable MFA"}
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
