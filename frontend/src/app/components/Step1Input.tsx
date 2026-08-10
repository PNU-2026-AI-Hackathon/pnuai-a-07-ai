import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { AlertCircle, ArrowRight, Building2, Factory, Loader2, MapPin, Shield, Users } from "lucide-react";
import { toast } from "sonner";
import { useSafety } from "../contexts/SafetyContext";
import { ApiError, safetyApi } from "../utils/api";
import type { WorkplaceRequest } from "../types/safety";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
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
  const { setWorkplace, setPreventionGuide, setChecklistItems, setChecklistAnswers, setRiskAssessment, startPreviewDiagnosis } = useSafety();
  const [form, setForm] = useState({
    name: "",
    industry: "",
    subIndustry: "",
    region: "부산",
    employeeCount: "20",
    address: "",
  });
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

  const startDiagnosis = async () => {
    if (!form.name.trim() || !form.industry || !form.region || !Number.isFinite(employeeCount) || employeeCount < 0) {
      setError("사업장명, 업종, 지역, 근로자 수를 올바르게 입력해 주세요.");
      return;
    }

    const request: WorkplaceRequest = {
      name: form.name.trim(),
      industry: form.industry,
      subIndustry: form.subIndustry.trim() || undefined,
      sizeClass: getSizeClass(employeeCount),
      region: form.region,
      employeeCount,
      address: form.address.trim() || undefined,
    };

    setIsSubmitting(true);
    setError("");
    try {
      const workplace = await safetyApi.createWorkplace(request);
      setWorkplace(workplace);
      setChecklistItems([]);
      setChecklistAnswers({});
      setRiskAssessment(null);
      try {
        const guide = await safetyApi.getPreventionGuide(workplace);
        setPreventionGuide(guide);
        toast.success("사업장이 등록되었습니다.", { description: "실제 서버 데이터로 예방 가이드를 만들었습니다." });
      } catch (guideError) {
        setPreventionGuide({ predictions: [] });
        toast.warning("사업장은 등록됐지만 예방 가이드를 불러오지 못했습니다.", {
          description: guideError instanceof Error ? guideError.message : "DB 스키마 상태를 확인해 주세요.",
        });
      }
      navigate("/report");
    } catch (caught) {
      if (import.meta.env.DEV) {
        startPreviewDiagnosis();
        toast.info("서버 연결 없이 예시 데이터로 예방 가이드를 표시합니다.");
        navigate("/report");
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
    <div className="container mx-auto max-w-4xl px-4 py-8 md:py-12">
      <header className="mb-8 text-center">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-blue-100 px-4 py-2 text-blue-700">
          <Shield className="h-5 w-5" />
          <span className="text-sm font-medium">STEP 1 / 4 · 사업장 등록</span>
        </div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">사업장 정보를 입력하세요</h1>
        <p className="mt-3 text-gray-600">입력한 정보는 서버에 저장되며 맞춤 예방 가이드에 사용됩니다.</p>
      </header>

      <Card className="border-2">
        <CardHeader>
          <CardTitle className="text-xl">진단 대상 사업장</CardTitle>
          <CardDescription>서버의 허용값과 일치하도록 업종과 지역을 선택해 주세요.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <Label htmlFor="workplace-name" className="flex items-center gap-2 font-semibold"><Building2 className="h-4 w-4 text-blue-600" />사업장명</Label>
            <Input id="workplace-name" value={form.name} onChange={(e) => update("name", e.target.value)} placeholder="예: 동헌금속" className="h-12" aria-invalid={Boolean(fieldErrors.name)} />
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
              <Input id="sub-industry" value={form.subIndustry} onChange={(e) => update("subIndustry", e.target.value)} placeholder="예: 금속가공" className="h-12" />
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
              <Input id="employee-count" type="number" min="0" value={form.employeeCount} onChange={(e) => update("employeeCount", e.target.value)} className="h-12" aria-invalid={Boolean(fieldErrors.employeeCount)} />
              <p className="text-sm text-gray-500">사업장 규모: <strong className="text-gray-800">{sizeClass}</strong></p>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="address" className="font-semibold">상세 주소 <span className="font-normal text-gray-500">선택</span></Label>
            <Input id="address" value={form.address} onChange={(e) => update("address", e.target.value)} placeholder="예: 부산 사상구" className="h-12" />
          </div>

          {error && <div role="alert" className="flex gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-red-900"><AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" /><p className="text-sm">{error}</p></div>}

          <Button onClick={startDiagnosis} disabled={isSubmitting} size="lg" className="h-14 w-full bg-blue-600 text-base font-semibold hover:bg-blue-700">
            {isSubmitting ? <><Loader2 className="h-5 w-5 animate-spin" />사업장 등록 중...</> : <>예방 가이드 확인하기<ArrowRight className="h-5 w-5" /></>}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
