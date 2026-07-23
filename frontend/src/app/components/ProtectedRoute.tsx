import { Navigate, Outlet, useLocation } from "react-router";
import { isAuthenticated } from "../utils/auth";

export default function ProtectedRoute() {
  const location = useLocation();

  if (import.meta.env.VITE_DEMO_MODE === "true") {
    return <Outlet />;
  }

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
