import { FormEvent, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { api, AuthResponse } from "../api";

export default function RegisterPage() {
  const [form, setForm] = useState({ username: "", email: "", password: "", fullName: "" });
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const { data } = await api.post<AuthResponse>("/auth/register", form);
      localStorage.setItem("token", data.token);
      localStorage.setItem("username", data.username);
      navigate("/");
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Registration failed");
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <form onSubmit={onSubmit} className="bg-white p-8 rounded-lg shadow w-full max-w-sm space-y-4">
        <h1 className="text-2xl font-semibold">Create account</h1>
        {error && <p className="text-red-600 text-sm">{error}</p>}
        {(["fullName", "username", "email", "password"] as const).map((field) => (
          <label key={field} className="block text-sm capitalize">
            {field === "fullName" ? "Full name" : field}
            <input
              type={field === "password" ? "password" : field === "email" ? "email" : "text"}
              className="mt-1 block w-full border rounded px-3 py-2"
              value={form[field]}
              onChange={(e) => setForm({ ...form, [field]: e.target.value })}
              required
              minLength={field === "password" ? 8 : undefined}
            />
          </label>
        ))}
        <button className="w-full bg-slate-900 text-white rounded py-2 font-medium" type="submit">
          Register
        </button>
        <p className="text-sm text-slate-600">
          Already have an account? <Link to="/login" className="underline">Sign in</Link>
        </p>
      </form>
    </div>
  );
}
