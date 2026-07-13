import { Outlet } from "react-router";
import { SafetyProvider } from "../contexts/SafetyContext";

export default function Root() {
  return (
    <SafetyProvider>
      <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-orange-50">
        <Outlet />
      </div>
    </SafetyProvider>
  );
}
