import { useQuery } from "@tanstack/react-query";
import { api } from "../api";

interface MeResponse {
  username: string;
  role: string;
}

export function useIsAdmin(): { isAdmin: boolean; isLoading: boolean } {
  const token = localStorage.getItem("token");
  const { data, isLoading } = useQuery<MeResponse>({
    queryKey: ["me"],
    queryFn: async () => (await api.get<MeResponse>("/me")).data,
    enabled: !!token,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  return {
    isAdmin: data?.role === "ADMIN",
    isLoading,
  };
}
