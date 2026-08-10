import { Navigate, Outlet, useLocation } from "react-router";
import { hasDemoSession, isAuthenticated } from "../utils/auth";

export default function ProtectedRoute() {
  const location = useLocation();
  // 터널 시연은 실제 JWT 인증을 사용하므로 데모 우회를 비활성화합니다.
  const isDemoMode = false;

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
