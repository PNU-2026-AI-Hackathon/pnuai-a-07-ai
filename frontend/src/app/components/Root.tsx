import { useEffect } from "react";
import { Outlet } from "react-router";
import { SafetyProvider, useSafety } from "../contexts/SafetyContext";
import { safetyApi } from "../utils/api";
import AppHeader from "./AppHeader";
import { Toaster } from "./ui/sonner";

export default function Root() {
  return (
    <SafetyProvider>
      <AppShell />
    </SafetyProvider>
  );
}

function AppShell() {
  const { setUser } = useSafety();

  useEffect(() => {
    let active = true;
    safetyApi.getMe().then((me) => {
      if (active) setUser(me);
    }).catch(() => {
      // 403 응답은 공통 API 계층에서 로그인 화면으로 이동시킵니다.
    });
    return () => { active = false; };
    // 사용자 확인은 보호된 앱 셸이 처음 열릴 때 한 번만 수행합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-orange-50">
      <AppHeader />
      <main><Outlet /></main>
      <Toaster richColors position="bottom-right" />
    </div>
  );
}
