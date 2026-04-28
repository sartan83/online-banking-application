import { Outlet, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import AppHeader from "./AppHeader";
import { useIdleTimeout } from "../hooks/useIdleTimeout";

export default function AuthenticatedLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useIdleTimeout(() => {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    localStorage.removeItem("role");
    queryClient.clear();
    toast("Session expired due to inactivity", { icon: "⏰" });
    navigate("/login");
  });

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />
      <main className="p-4 sm:p-6 max-w-5xl mx-auto">
        <Outlet />
      </main>
    </div>
  );
}
