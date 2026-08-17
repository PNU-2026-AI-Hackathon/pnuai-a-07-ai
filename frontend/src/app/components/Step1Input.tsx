import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { AlertCircle, ArrowRight, Building2, Factory, ListFilter, Loader2, MapPin, Shield, Users } from "lucide-react";
import { toast } from "sonner";
import { useSafety } from "../contexts/SafetyContext";
import type { RiskScopeCode, WorkplaceRequest } from "../types/safety";
import { ApiError, safetyApi } from "../utils/api";
import { RISK_SCOPE_OPTIONS } from "../utils/riskScopes";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Checkbox } from "./ui/checkbox";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";

const industries = ["제조업", "건설업", "운수창고통신업", "전기가스증기수도사업"];
const regions = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"];

function getSizeClass(count: number) {
  if (count < 5) return "5인 미만";
  if (count < 10) return "5~9인";
  if (count < 20) return "10~19인";
  if (count < 30) return "20~29인";
  if (count < 50) return "30~49인";
  if (count < 100) return "50~99인";
  if (count < 300) return "100~299인";
  if (count < 500) return "300~499인";
  if (count < 1000) return "500~999인";
  return "1,000인 이상";
}

export default function Step1Input() {
  const navigate = useNavigate();
  const {
    setWorkplace, setPreventionGuide, setChecklistItems, setSelectedRiskScopes,
    setChecklistAnswers, setRiskAssessment, startPreviewDiagnosis,
  } = useSafety();
  const [form, setForm] = useState({
    name: "", industry: "", subIndustry: "", region: "부산", employeeCount: "20", address: "",
  });
  const [selectedScopes, setSelectedScopes] = useState<RiskScopeCode[]>([]);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const employeeCount = Number(form.employeeCount);
  const sizeClass = useMemo(
    () => Number.isFinite(employeeCount) && employeeCount >= 0 ? getSizeClass(employeeCount) : "-",
    [employeeCount],
  );

  const update = (field: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
    setError("");
    setFieldErrors((current) => ({ ...current, [field]: "" }));
  };

  const toggleScope = (code: RiskScopeCode, checked: boolean) => {
    setSelectedScopes((current) => {
      if (!checked) return current.filter((item) => item !== code);
      if (code === "GENERAL") return ["GENERAL"];
      return Array.from(new Set([...current.filter((item) => item !== "GENERAL"), code]));
    });
    setError("");
    setFieldErrors((current) => ({ ...current, riskScopes: "" }));
  };

  const startDiagnosis = async () => {
    const nextFieldErrors: Record<string, string> = {};
    if (!form.name.trim()) nextFieldErrors.name = "사업장명을 입력해 주세요.";
    if (!form.industry) nextFieldErrors.industry = "업종을 선택해 주세요.";
    if (!form.region) nextFieldErrors.region = "지역을 선택해 주세요.";
    if (!Number.isFinite(employeeCount) || employeeCount < 0) nextFieldErrors.employeeCount = "근로자 수를 확인해 주세요.";
    if (selectedScopes.length === 0) nextFieldErrors.riskScopes = "현장에 해당하는 작업·위험 범주를 하나 이상 선택해 주세요.";
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors);
      setError("필수 정보와 현장 작업·위험 범주를 확인해 주세요.");
      return;
    }

    const request: WorkplaceRequest = {
      name: form.name.trim(), industry: form.industry,
      subIndustry: form.subIndustry.trim() || undefined,
      sizeClass: getSizeClass(employeeCount), region: form.region, employeeCount,
      address: form.address.trim() || undefined,
    };

    setIsSubmitting(true);
    setError("");
    try {
      const workplace = await safetyApi.createWorkplace(request);
      setWorkplace(workplace);
      setPreventionGuide(null);
      setChecklistItems([]);
      setSelectedRiskScopes(selectedScopes);
      setChecklistAnswers({});
      setRiskAssessment(null);
      toast.success("점검 대상이 선별되었습니다.", { description: "관련성이 높은 중대 SIF 25~35개를 불러옵니다." });
      navigate("/checklist");
    } catch (caught) {
      if (import.meta.env.DEV) {
        startPreviewDiagnosis(selectedScopes, {
          ...request,
          id: 0,
          subIndustry: request.subIndustry ?? null,
          employeeCount: request.employeeCount ?? null,
          address: request.address ?? null,
          machineType: null,
          machineCount: null,
          safetyDeviceStatus: null,
          storageLocation: null,
          storageMethod: null,
          createdAt: new Date().toISOString(),
        });
        toast.info("서버 연결 없이 예시 SIF 체크리스트를 표시합니다.");
        navigate("/checklist");
        return;
      }
      if (caught instanceof ApiError) {
        setError(caught.message);
        setFieldErrors(caught.fields);
      } else {
        setError("사업장 정보를 저장하지 못했습니다.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8 md:py-12">
      <header className="mb-8 text-center">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-blue-100 px-4 py-2 text-blue-700">
          <Shield className="h-5 w-5" />
          <span className="text-sm font-medium">STEP 1 / 4 · 점검 대상 선별</span>
        </div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">우리 현장에 해당하는 범위를 선택하세요</h1>
        <p className="mx-auto mt-3 max-w-2xl text-gray-600">사업장 기본정보와 실제 작업 범주를 기준으로 전체 98개 중 관련성이 높은 SIF 25~35개를 선별합니다.</p>
      </header>

      <div className="space-y-6">
        <Card className="border-2">
          <CardHeader>
            <CardTitle className="text-xl">사업장 기본정보</CardTitle>
            <CardDescription>업종은 참고 조건으로 사용하며, 구체적인 점검 문항은 아래 작업·위험 범주를 중심으로 고릅니다.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="workplace-name" className="flex items-center gap-2 font-semibold"><Building2 className="h-4 w-4 text-blue-600" />사업장명</Label>
              <Input id="workplace-name" value={form.name} onChange={(event) => update("name", event.target.value)} placeholder="예: 부산안전산업" className="h-12" aria-invalid={Boolean(fieldErrors.name)} />
              {fieldErrors.name && <p className="text-sm text-red-600">{fieldErrors.name}</p>}
            </div>

            <div className="grid gap-5 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="industry" className="flex items-center gap-2 font-semibold"><Factory className="h-4 w-4 text-blue-600" />업종</Label>
                <Select value={form.industry} onValueChange={(value) => update("industry", value)}>
                  <SelectTrigger id="industry" className="h-12" aria-invalid={Boolean(fieldErrors.industry)}><SelectValue placeholder="업종 선택" /></SelectTrigger>
                  <SelectContent>{industries.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent>
                </Select>
                {fieldErrors.industry && <p className="text-sm text-red-600">{fieldErrors.industry}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="sub-industry" className="font-semibold">세부 업종 <span className="font-normal text-gray-500">선택</span></Label>
                <Input id="sub-industry" value={form.subIndustry} onChange={(event) => update("subIndustry", event.target.value)} placeholder="예: 금속가공, 창고업, 시설관리" className="h-12" />
              </div>
            </div>

            <div className="grid gap-5 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="region" className="flex items-center gap-2 font-semibold"><MapPin className="h-4 w-4 text-blue-600" />지역</Label>
                <Select value={form.region} onValueChange={(value) => update("region", value)}>
                  <SelectTrigger id="region" className="h-12" aria-invalid={Boolean(fieldErrors.region)}><SelectValue /></SelectTrigger>
                  <SelectContent>{regions.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent>
                </Select>
                {fieldErrors.region && <p className="text-sm text-red-600">{fieldErrors.region}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="employee-count" className="flex items-center gap-2 font-semibold"><Users className="h-4 w-4 text-green-600" />근로자 수</Label>
                <Input id="employee-count" type="number" min="0" value={form.employeeCount} onChange={(event) => update("employeeCount", event.target.value)} className="h-12" aria-invalid={Boolean(fieldErrors.employeeCount)} />
                <p className="text-sm text-gray-500">사업장 규모: <strong className="text-gray-800">{sizeClass}</strong></p>
                {fieldErrors.employeeCount && <p className="text-sm text-red-600">{fieldErrors.employeeCount}</p>}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="address" className="font-semibold">상세 주소 <span className="font-normal text-gray-500">선택</span></Label>
              <Input id="address" value={form.address} onChange={(event) => update("address", event.target.value)} placeholder="예: 부산 사상구" className="h-12" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-2 border-blue-200">
          <CardHeader>
            <div className="flex items-start gap-3">
              <ListFilter className="mt-0.5 h-5 w-5 shrink-0 text-blue-700" />
              <div>
                <CardTitle className="text-xl">현장 작업·위험 범주</CardTitle>
                <CardDescription className="mt-1">실제로 존재하는 범주를 모두 선택하세요. 세부 기계명이나 적재 위치는 입력하지 않아도 됩니다.</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-5">
            <fieldset aria-invalid={Boolean(fieldErrors.riskScopes)}>
              <legend className="sr-only">현장 작업·위험 범주 선택</legend>
              <div className="grid gap-3 md:grid-cols-2">
                {RISK_SCOPE_OPTIONS.map((item) => {
                  const checked = selectedScopes.includes(item.code);
                  const id = `risk-scope-${item.code}`;
                  return (
                    <label key={item.code} htmlFor={id} className={`flex min-h-24 cursor-pointer items-start gap-3 rounded-md border-2 p-4 transition-colors ${checked ? "border-blue-500 bg-blue-50" : "border-gray-200 bg-white hover:border-blue-200"}`}>
                      <Checkbox id={id} checked={checked} onCheckedChange={(value) => toggleScope(item.code, value === true)} className="mt-0.5" />
                      <span>
                        <span className="block font-semibold text-gray-950">{item.label}</span>
                        <span className="mt-1 block text-sm leading-6 text-gray-600">{item.description}</span>
                      </span>
                    </label>
                  );
                })}
              </div>
            </fieldset>
            {fieldErrors.riskScopes && <p className="text-sm text-red-600">{fieldErrors.riskScopes}</p>}
            <div className="rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm leading-6 text-blue-950">
              <strong>{selectedScopes.length}개 범주 선택</strong> · 공통 중대문항과 선택 범주의 문항을 합친 뒤 위험가중치 순으로 25~35개를 제공합니다.
            </div>
          </CardContent>
        </Card>

        {error && <div role="alert" className="flex gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-red-900"><AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" /><p className="text-sm">{error}</p></div>}

        <Button onClick={startDiagnosis} disabled={isSubmitting} size="lg" className="h-14 w-full bg-blue-600 text-base font-semibold hover:bg-blue-700">
          {isSubmitting ? <><Loader2 className="h-5 w-5 animate-spin" />점검 대상 선별 중...</> : <>맞춤 SIF 체크리스트 시작하기<ArrowRight className="h-5 w-5" /></>}
        </Button>
      </div>
    </div>
  );
}
