import { Navigate, Outlet, useLocation } from "react-router";
import { hasDemoSession, isAuthenticated } from "../utils/auth";

export default function ProtectedRoute() {
  const location = useLocation();
  const isDemoMode = import.meta.env.VITE_DEMO_MODE === "true";

  if (isDemoMode && !hasDemoSession()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (isDemoMode) {
    return <Outlet />;
  }

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
