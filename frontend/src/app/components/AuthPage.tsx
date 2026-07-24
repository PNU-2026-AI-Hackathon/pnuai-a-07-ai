import { FormEvent, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { AlertCircle, ArrowRight, LockKeyhole, Mail, Phone, Shield, UserRound } from "lucide-react";
import { toast } from "sonner";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./ui/tabs";
import { saveAuthToken, startDemoSession, type AuthResponse } from "../utils/auth";

type AuthMode = "login" | "register";

type AuthForm = {
  email: string;
  password: string;
  name: string;
  phone: string;
};

const initialForm: AuthForm = {
  email: "",
  password: "",
  name: "",
  phone: "",
};

async function getErrorMessage(response: Response, mode: AuthMode) {
  const fallbackMessage =
    mode === "login"
      ? "가입한 이메일과 비밀번호를 입력해야 다음 단계로 넘어갈 수 있습니다."
      : "회원가입 정보를 확인해주세요. 이미 가입된 이메일이면 로그인으로 진행하세요.";

  try {
    const data = (await response.json()) as { error?: string; message?: string };
    return data.error || data.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

export default function AuthPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const isDemoMode = import.meta.env.VITE_DEMO_MODE === "true";
  const [mode, setMode] = useState<AuthMode>("login");
  const [form, setForm] = useState<AuthForm>(initialForm);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const redirectTo = useMemo(() => {
    const state = location.state as { from?: { pathname?: string } } | null;
    return state?.from?.pathname || "/";
  }, [location.state]);

  const updateField = (field: keyof AuthForm, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
    setError("");
  };

  const beginDemo = () => {
    startDemoSession();
    toast.success("데모 화면으로 이동합니다.");
    navigate(redirectTo, { replace: true });
  };

  const validate = () => {
    if (!form.email.trim()) return "이메일을 입력해주세요.";
    if (!form.email.includes("@")) return "이메일 형식을 확인해주세요.";
    if (!form.password.trim()) return "비밀번호를 입력해주세요.";
    if (mode === "register" && !form.name.trim()) return "이름을 입력해주세요.";
    return "";
  };

  const submitAuth = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const validationMessage = validate();
    if (validationMessage) {
      setError(validationMessage);
      return;
    }

    setIsSubmitting(true);
    setError("");

    const endpoint = mode === "login" ? "/api/auth/login" : "/api/auth/register";
    const payload =
      mode === "login"
        ? { email: form.email, password: form.password }
        : {
            email: form.email,
            password: form.password,
            name: form.name,
            phone: form.phone || undefined,
          };

    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(await getErrorMessage(response, mode));
      }

      const authResponse = (await response.json()) as AuthResponse;
      saveAuthToken(authResponse);
      toast.success(mode === "login" ? "로그인되었습니다." : "회원가입이 완료되었습니다.");
      navigate(redirectTo, { replace: true });
    } catch (submitError) {
      const message =
        submitError instanceof TypeError
          ? "백엔드 서버에 연결하지 못했습니다. 서버가 켜져 있는지 확인하고 프론트 dev server를 다시 시작해주세요."
          : submitError instanceof Error
            ? submitError.message
            : "요청을 처리하지 못했습니다.";
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="container mx-auto flex min-h-screen max-w-6xl items-center px-4 py-8 md:py-12">
      <div className="grid w-full gap-8 lg:grid-cols-[1fr_440px] lg:items-center">
        <section className="space-y-6">
          <div className="inline-flex items-center gap-2 rounded-full bg-blue-100 px-4 py-2 text-blue-700">
            <Shield className="h-5 w-5" />
            <span className="text-sm font-medium">AI 기반 산업안전 진단 시스템</span>
          </div>

          <div className="space-y-4">
            <h1 className="max-w-2xl text-3xl font-bold leading-tight text-gray-950 md:text-4xl">
              <span className="block">사업장 위험 진단을</span>
              <span className="block">시작하세요</span>
            </h1>
            <p className="max-w-md text-base leading-7 text-gray-600">
              <span className="block">
                {isDemoMode ? "데모를 시작하고 사업장 정보를 입력하면" : "로그인 후 사업장 정보를 입력하면"}
              </span>
              <span className="block">위험 리포트와 예방 체크리스트를 확인할 수 있습니다.</span>
            </p>
          </div>

          <div className="grid gap-3 text-sm text-gray-700 sm:grid-cols-3">
            <div className="rounded-md border border-blue-100 bg-white px-4 py-3">
              <p className="font-semibold text-gray-950">1. 정보 입력</p>
              <p className="mt-1 text-gray-600">업종과 규모 선택</p>
            </div>
            <div className="rounded-md border border-orange-100 bg-white px-4 py-3">
              <p className="font-semibold text-gray-950">2. 위험 분석</p>
              <p className="mt-1 text-gray-600">위험도와 원인 확인</p>
            </div>
            <div className="rounded-md border border-green-100 bg-white px-4 py-3">
              <p className="font-semibold text-gray-950">3. 예방 실행</p>
              <p className="mt-1 text-gray-600">체크리스트 적용</p>
            </div>
          </div>
        </section>

        <Card className="border-2 shadow-lg">
          <CardHeader>
            <CardTitle className="text-xl">
              {isDemoMode ? "데모로 계속하기" : "계정으로 계속하기"}
            </CardTitle>
            <CardDescription>
              {isDemoMode
                ? "현재는 프론트엔드 데모 모드입니다. 로그인 없이 주요 화면을 확인할 수 있습니다."
                : "백엔드 인증 API와 연결된 로그인/회원가입 화면입니다."}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isDemoMode ? (
              <div className="space-y-4">
                <div className="rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm leading-6 text-blue-950">
                  데모에서 입력한 정보는 서버에 저장되지 않습니다. 주요 진단 화면과 이동 흐름을 자유롭게 확인하세요.
                </div>
                <Button
                  type="button"
                  size="lg"
                  onClick={beginDemo}
                  className="h-12 w-full bg-blue-600 text-base font-semibold hover:bg-blue-700"
                >
                  <span>데모로 진단 시작</span>
                  <ArrowRight className="h-5 w-5" />
                </Button>
              </div>
            ) : (
              <Tabs value={mode} onValueChange={(value) => {
                setMode(value as AuthMode);
                setError("");
              }}>
                <TabsList className="mb-6 grid h-11 w-full grid-cols-2 rounded-md">
                  <TabsTrigger className="rounded-md" value="login">로그인</TabsTrigger>
                  <TabsTrigger className="rounded-md" value="register">회원가입</TabsTrigger>
                </TabsList>

                <form onSubmit={submitAuth} className="space-y-5">
                  <TabsContent value="login" className="mt-0 space-y-5">
                    <EmailPasswordFields form={form} error={error} updateField={updateField} />
                  </TabsContent>

                  <TabsContent value="register" className="mt-0 space-y-5">
                    <div className="space-y-2">
                      <Label htmlFor="name" className="flex items-center gap-2 font-semibold">
                        <UserRound className="h-4 w-4 text-blue-600" />
                        이름
                      </Label>
                      <Input
                        id="name"
                        value={form.name}
                        onChange={(event) => updateField("name", event.target.value)}
                        placeholder="이름을 입력하세요"
                        className="h-12"
                        aria-invalid={Boolean(error && !form.name.trim())}
                      />
                    </div>

                    <EmailPasswordFields form={form} error={error} updateField={updateField} />

                    <div className="space-y-2">
                      <Label htmlFor="phone" className="flex items-center gap-2 font-semibold">
                        <Phone className="h-4 w-4 text-green-600" />
                        전화번호 <span className="text-sm font-normal text-gray-500">선택</span>
                      </Label>
                      <Input
                        id="phone"
                        value={form.phone}
                        onChange={(event) => updateField("phone", event.target.value)}
                        placeholder="010-1234-5678"
                        className="h-12"
                      />
                    </div>
                  </TabsContent>

                  {error && (
                    <div
                      role="alert"
                      className="flex items-start gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-red-900"
                    >
                      <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-red-600" />
                      <p className="text-sm">{error}</p>
                    </div>
                  )}

                  <Button
                    type="submit"
                    size="lg"
                    disabled={isSubmitting}
                    className="h-12 w-full bg-blue-600 text-base font-semibold hover:bg-blue-700"
                  >
                    <span>{isSubmitting ? "처리 중..." : mode === "login" ? "로그인하고 진단 시작" : "가입하고 진단 시작"}</span>
                    <ArrowRight className="h-5 w-5" />
                  </Button>
                </form>
              </Tabs>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function EmailPasswordFields({
  form,
  error,
  updateField,
}: {
  form: AuthForm;
  error: string;
  updateField: (field: keyof AuthForm, value: string) => void;
}) {
  return (
    <>
      <div className="space-y-2">
        <Label htmlFor="email" className="flex items-center gap-2 font-semibold">
          <Mail className="h-4 w-4 text-blue-600" />
          이메일
        </Label>
        <Input
          id="email"
          type="email"
          value={form.email}
          onChange={(event) => updateField("email", event.target.value)}
          placeholder="you@example.com"
          className="h-12"
          aria-invalid={Boolean(error && (!form.email.trim() || !form.email.includes("@")))}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="password" className="flex items-center gap-2 font-semibold">
          <LockKeyhole className="h-4 w-4 text-blue-600" />
          비밀번호
        </Label>
        <Input
          id="password"
          type="password"
          value={form.password}
          onChange={(event) => updateField("password", event.target.value)}
          placeholder="비밀번호를 입력하세요"
          className="h-12"
          aria-invalid={Boolean(error && !form.password.trim())}
        />
      </div>
    </>
  );
}
