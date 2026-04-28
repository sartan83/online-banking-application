import { FormEvent, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { api, LoginResponse } from "../api";

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const { data } = await api.post<LoginResponse>("/auth/login", { username, password });
      if (data.mfaRequired && data.partialToken) {
        navigate("/login/mfa", { state: { partialToken: data.partialToken, username } });
        return;
      }
      localStorage.setItem("token", data.accessToken);
      localStorage.setItem("refreshToken", data.refreshToken);
      localStorage.setItem("username", username);
      navigate("/");
    } catch {
      setError("Invalid credentials");
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
        <h1 className="text-2xl font-semibold">Sign in</h1>
        {error && <p className="text-red-600 text-sm">{error}</p>}
        <label className="block text-sm">
          Username
          <input
            className="mt-1 block w-full border rounded px-3 py-2"
            value={username}
            onChange={(e) => {
              setUsername(e.target.value);
            }}
            required
          />
        </label>
        <label className="block text-sm">
          Password
          <input
            type="password"
            className="mt-1 block w-full border rounded px-3 py-2"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
            }}
            required
          />
        </label>
        <button className="w-full bg-slate-900 text-white rounded py-2 font-medium" type="submit">
          Sign in
        </button>
        <p className="text-sm text-slate-600">
          No account? <Link to="/register" className="underline">Register</Link>
        </p>
      </form>
    </div>
  );
}
