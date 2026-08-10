import { AlertTriangle, BookOpenCheck, ClipboardCheck, LogOut, ShieldCheck } from "lucide-react";
import { NavLink, useNavigate } from "react-router";
import { useSafety } from "../contexts/SafetyContext";
import { clearAuthToken } from "../utils/auth";
import { Button } from "./ui/button";

const navItems = [
  { to: "/", label: "안전 진단", icon: ClipboardCheck },
  { to: "/laws", label: "법령 상담", icon: BookOpenCheck },
  { to: "/accident-response", label: "사고 대처", icon: AlertTriangle },
];

export default function AppHeader() {
  const navigate = useNavigate();
  const { user, resetDiagnosis } = useSafety();

  const logout = () => {
    clearAuthToken();
    resetDiagnosis();
    navigate("/login", { replace: true });
  };

  return (
    <header className="border-b border-blue-100 bg-white/95">
      <div className="container mx-auto flex max-w-6xl flex-col gap-3 px-4 py-3 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center justify-between gap-4">
          <NavLink to="/" className="flex items-center gap-2 font-bold text-gray-950">
            <span className="rounded-md bg-blue-600 p-1.5 text-white"><ShieldCheck className="h-5 w-5" /></span>
            SafeWork AI
          </NavLink>
          <span className="text-sm text-gray-600 md:hidden">{user ? `${user.name} 사장님` : "사용자 확인 중"}</span>
        </div>
        <nav className="flex min-w-0 items-center gap-1 overflow-x-auto" aria-label="주요 메뉴">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === "/"} className={({ isActive }) => `inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-md px-3 text-sm font-medium ${isActive ? "bg-blue-100 text-blue-800" : "text-gray-600 hover:bg-gray-100 hover:text-gray-950"}`}>
              <Icon className="h-4 w-4" />{label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden items-center gap-3 md:flex">
          <span className="text-sm text-gray-600">{user ? `${user.name} 사장님, 안녕하세요` : "사용자 확인 중"}</span>
          <Button type="button" variant="ghost" size="sm" onClick={logout} aria-label="로그아웃"><LogOut className="h-4 w-4" />로그아웃</Button>
        </div>
      </div>
    </header>
  );
}
