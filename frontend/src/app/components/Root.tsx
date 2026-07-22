import { Outlet } from "react-router";
import { SafetyProvider } from "../contexts/SafetyContext";
import { Toaster } from "./ui/sonner";

export default function Root() {
  return (
    <SafetyProvider>
      <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-orange-50">
        <Outlet />
        <Toaster richColors position="bottom-right" />
      </div>
    </SafetyProvider>
  );
}
