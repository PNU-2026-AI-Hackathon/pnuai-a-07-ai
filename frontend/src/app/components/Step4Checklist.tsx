import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { AlertCircle, ArrowRight, CheckCircle2, FileText, ListFilter, Loader2, RefreshCw, ShieldAlert } from "lucide-react";
import { toast } from "sonner";
import { useSafety } from "../contexts/SafetyContext";
import type { Answer, RiskGrade, RiskScopeCode } from "../types/safety";
import { ApiError, safetyApi } from "../utils/api";
import { createPreviewChecklistItems, previewRiskAssessment } from "../utils/devPreview";
import { riskScopeLabel } from "../utils/riskScopes";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Progress } from "./ui/progress";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";

const gradeConfig: Record<RiskGrade, { label: string; className: string }> = {
  LOW: { label: "양호", className: "bg-green-600" },
  MEDIUM: { label: "보통", className: "bg-yellow-600" },
  HIGH: { label: "위험", className: "bg-orange-600" },
  CRITICAL: { label: "매우 위험", className: "bg-red-700" },
};

const MAX_CHECKLIST_ITEMS = 35;

export default function Step4Checklist() {
  const navigate = useNavigate();
  const {
    workplace,
    checklistItems,
    setChecklistItems,
    selectedRiskScopes,
    checklistAnswers: answers,
    setChecklistAnswers,
    riskAssessment,
    setRiskAssessment,
  } = useSafety();
  const [category, setCategory] = useState("ALL");
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const isPreview = import.meta.env.DEV && workplace?.id === 0;

  const personalizedItems = useMemo(
    () => [...checklistItems]
      .sort((a, b) => Number(b.isCritical) - Number(a.isCritical) || b.riskWeight - a.riskWeight)
      .slice(0, MAX_CHECKLIST_ITEMS),
    [checklistItems],
  );
  const categories = useMemo(() => Array.from(new Set(personalizedItems.map((item) => item.category))).sort(), [personalizedItems]);
  const visibleItems = category === "ALL" ? personalizedItems : personalizedItems.filter((item) => item.category === category);
  const answeredCount = Object.keys(answers).filter((code) => personalizedItems.some((item) => item.itemCode === code)).length;
  const completionRate = personalizedItems.length ? Math.round((answeredCount / personalizedItems.length) * 100) : 0;

  const loadChecklist = async (riskScopes: RiskScopeCode[]) => {
    if (!workplace || riskScopes.length === 0) return;
    setIsLoading(true);
    setError("");
    try {
      if (isPreview) {
        setChecklistItems(createPreviewChecklistItems(riskScopes).slice(0, MAX_CHECKLIST_ITEMS));
      } else {
        const items = await safetyApi.getChecklistItems(workplace.id, true, riskScopes, MAX_CHECKLIST_ITEMS);
        setChecklistItems(items);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "체크리스트를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (!workplace) {
      navigate("/", { replace: true });
      return;
    }

    if (selectedRiskScopes.length > 0 && checklistItems.length === 0) {
      void loadChecklist(selectedRiskScopes);
    } else if (selectedRiskScopes.length === 0 && checklistItems.length > 0 && !isPreview) {
      setChecklistItems([]);
    }
    // 진단 세션이 바뀌거나 체크리스트에 처음 진입할 때만 기준정보와 문항을 조회합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workplace?.id]);

  if (!workplace) return null;

  const setAnswer = (itemCode: string, answer: Answer) => {
    setChecklistAnswers({ ...answers, [itemCode]: answer });
    setError("");
  };

  const submitChecklist = async () => {
    if (answeredCount !== personalizedItems.length) {
      setError(`표시된 문항에 모두 답해 주세요. 아직 ${personalizedItems.length - answeredCount}개가 남았습니다.`);
      return;
    }

    setIsSubmitting(true);
    setError("");
    if (isPreview) {
      setRiskAssessment(previewRiskAssessment);
      toast.success("예시 위험도 진단이 완료되었습니다.");
      setIsSubmitting(false);
      window.scrollTo({ top: 0, behavior: "smooth" });
      return;
    }
    try {
      const result = await safetyApi.submitChecklist(
        workplace.id,
        personalizedItems.map((item) => ({ itemCode: item.itemCode, answer: answers[item.itemCode] })),
      );
      setRiskAssessment(result.riskAssessment);
      toast.success("위험도 진단이 완료되었습니다.", { description: `${result.answeredItems}개 답변이 위험도 계산에 반영되었습니다.` });
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "체크리스트를 제출하지 못했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const grade = riskAssessment?.riskGrade ? gradeConfig[riskAssessment.riskGrade] : null;
  const base = riskAssessment?.baseComponent ?? 0;
  const checklist = riskAssessment?.checklistComponent ?? 0;

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <header className="mb-8 text-center">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-blue-100 px-4 py-2 text-blue-800"><CheckCircle2 className="h-5 w-5" /><span className="text-sm font-medium">STEP 2 / 4 · 중대 체크리스트</span></div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">현장 작업에 맞춰 점검하세요</h1>
        <p className="mt-3 text-gray-600">STEP 1에서 고른 범주를 기준으로 전체 SIF를 25~35개로 줄였습니다. 해당 없음은 위험도 계산에서 제외됩니다.</p>
        {isPreview && <p className="mt-2 text-sm text-gray-500">예시 데이터로 표시한 화면입니다.</p>}
      </header>

      {riskAssessment && (
        <Card className="mb-8 border-2 border-orange-200">
          <CardHeader>
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div><CardTitle className="text-xl">최종 위험도 진단</CardTitle><CardDescription>체크리스트 제출과 동시에 서버가 계산한 결과입니다.</CardDescription></div>
              <div className="text-right">{grade ? <Badge className={grade.className}>{grade.label}</Badge> : <Badge variant="outline">통계 부족</Badge>}<p className="mt-2 text-4xl font-bold text-gray-950">{riskAssessment.riskScore === null ? "산정 불가" : `${riskAssessment.riskScore.toFixed(1)}점`}</p></div>
            </div>
          </CardHeader>
          <CardContent className="space-y-5">
            <div>
              <div className="mb-2 flex justify-between text-sm"><span>동종 사업장 기본 위험 <strong>{base.toFixed(1)}</strong></span><span>우리 사업장 미비 <strong>{checklist.toFixed(1)}</strong></span></div>
              <div className="flex h-5 overflow-hidden rounded-full bg-gray-100" aria-label={`기본 위험 ${base.toFixed(1)}점, 체크리스트 위험 ${checklist.toFixed(1)}점`}><div className="bg-blue-600" style={{ width: `${base}%` }} /><div className="bg-orange-600" style={{ width: `${checklist}%` }} /></div>
              <div className="mt-2 flex flex-wrap gap-4 text-xs text-gray-600"><span>● <span className="text-blue-700">기본 위험</span></span><span>● <span className="text-orange-700">미비 항목 위험</span></span></div>
            </div>
            <div className="grid gap-3 rounded-md bg-gray-50 p-4 text-sm sm:grid-cols-3">
              <p><span className="text-gray-500">최우선 재해유형</span><br /><strong>{riskAssessment.topRisks?.[0]?.type ?? riskAssessment.topAccidentType ?? "정보 없음"}</strong></p>
              <p><span className="text-gray-500">진단 방식</span><br /><strong>{riskAssessment.method}</strong></p>
              <p><span className="text-gray-500">통계 매칭</span><br /><strong>{riskAssessment.matchLevel ?? "정보 없음"}</strong></p>
            </div>
            {(riskAssessment.topRisks ?? []).length > 0 ? (
              <div className="grid gap-4 md:grid-cols-2">
                <section aria-labelledby="ml-risk-title" className="rounded-md border border-orange-200 bg-orange-50 p-4"><h3 id="ml-risk-title" className="font-semibold text-gray-950">종합 위험유형 비중</h3><ul className="mt-3 space-y-3">{(riskAssessment.topRisks ?? []).slice(0, 3).map((item) => <li key={item.type} className="text-sm"><div className="flex items-center justify-between gap-3"><span>{item.type}</span><strong className="text-orange-800">{(item.probability * 100).toFixed(1)}%</strong></div>{item.basis && <p className="mt-1 text-xs leading-5 text-gray-600">{item.basis}</p>}</li>)}</ul></section>
                <section aria-labelledby="ml-severity-title" className="rounded-md border border-blue-200 bg-blue-50 p-4"><h3 id="ml-severity-title" className="font-semibold text-gray-950">예상 피해 심각도</h3>{(riskAssessment.severityPrediction ?? []).length ? <ul className="mt-3 space-y-2">{(riskAssessment.severityPrediction ?? []).slice(0, 3).map((item) => <li key={item.label} className="flex items-center justify-between gap-3 text-sm"><span>{item.label}</span><strong className="text-blue-800">{(item.probability * 100).toFixed(1)}%</strong></li>)}</ul> : <p className="mt-3 text-sm text-gray-600">심각도 예측 데이터가 없습니다.</p>}</section>
              </div>
            ) : <p className="rounded-md bg-gray-50 px-4 py-3 text-sm text-gray-600">ML 서버를 사용할 수 없어 통계 기반 점수만 표시합니다.</p>}
            {riskAssessment.matchLevel && riskAssessment.matchLevel !== "EXACT" && <div className="flex gap-2 rounded-md border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-900"><ShieldAlert className="h-5 w-5 shrink-0" />유사 사업장 데이터가 적어 이 결과는 참고치로 활용해 주세요.</div>}
            <div className="rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm leading-6 text-blue-900">위 결과는 사업장 정보와 체크리스트 미비 항목을 모두 반영했습니다. 다음 단계에서 관련 사고사례를 확인하세요.</div>
            <Button onClick={() => navigate("/cases")} size="lg" className="h-12 w-full bg-blue-600 hover:bg-blue-700">맞춤 유사 재해사례 확인<ArrowRight className="h-5 w-5" /></Button>
          </CardContent>
        </Card>
      )}

      <Card className="mb-6 border-2 border-blue-200">
        <CardHeader>
          <div className="flex items-start gap-3"><ListFilter className="mt-0.5 h-5 w-5 shrink-0 text-blue-700" /><div><CardTitle className="text-xl">STEP 1 선택 범위가 적용되었습니다</CardTitle><CardDescription className="mt-1">공통 중대문항과 선택 범주의 문항을 우선하고, 부족한 경우 업종 고위험 문항으로 보완했습니다.</CardDescription></div></div>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-2">{selectedRiskScopes.map((scope) => <Badge key={scope} variant="secondary">{riskScopeLabel(scope)}</Badge>)}</div>
          <div className="mt-4 flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-gray-600"><strong className="text-gray-950">{personalizedItems.length}개 SIF</strong> 선별 · 허용 범위 25~35개</p>
            <Button variant="outline" onClick={() => navigate("/")}>범주 다시 선택</Button>
          </div>
        </CardContent>
      </Card>

      {error && <div role="alert" className="mb-6 flex gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-red-900"><AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" /><p className="text-sm">{error}</p></div>}

      {isLoading ? (
        <Card><CardContent className="flex items-center justify-center gap-3 py-16 text-gray-600"><Loader2 className="h-6 w-6 animate-spin text-blue-600" />선택한 작업의 중대 문항을 불러오는 중입니다...</CardContent></Card>
      ) : selectedRiskScopes.length === 0 ? (
        <Card><CardContent className="flex flex-col items-center gap-3 py-12 text-center"><FileText className="h-8 w-8 text-gray-400" /><p className="font-medium text-gray-900">STEP 1에서 작업·위험 범주를 먼저 선택해 주세요.</p><Button variant="outline" onClick={() => navigate("/")}>범주 선택으로 돌아가기</Button></CardContent></Card>
      ) : personalizedItems.length === 0 ? (
        <Card><CardContent className="flex flex-col items-center gap-4 py-12 text-center"><FileText className="h-8 w-8 text-gray-400" /><p className="text-gray-600">선택한 범주에 연결된 중대 문항이 없습니다.</p><Button variant="outline" onClick={() => loadChecklist(selectedRiskScopes)}><RefreshCw className="h-4 w-4" />다시 불러오기</Button></CardContent></Card>
      ) : (
        <>
          <Card className="mb-6 border-2">
            <CardContent className="p-5">
              <div className="mb-3 flex flex-wrap items-end justify-between gap-3"><div><h2 className="font-semibold text-gray-950">맞춤 문항 응답 진행률</h2><p className="text-sm text-gray-600">{answeredCount} / {personalizedItems.length}개 응답 · 선택 범주 {selectedRiskScopes.length}개 기준</p></div><strong className="text-2xl text-blue-700">{completionRate}%</strong></div>
              <Progress value={completionRate} className="h-3" />
              <div className="mt-4 flex flex-wrap gap-2">{selectedRiskScopes.map((scope) => <Badge key={scope} variant="secondary">{riskScopeLabel(scope)}</Badge>)}</div>
              <div className="mt-5 max-w-xs"><Select value={category} onValueChange={setCategory}><SelectTrigger aria-label="재해유형 필터"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="ALL">전체 재해유형 ({personalizedItems.length})</SelectItem>{categories.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div>
            </CardContent>
          </Card>

          <div className="space-y-4">
            {visibleItems.map((item, index) => (
              <Card key={item.itemCode} className={`border-2 ${answers[item.itemCode] === "NO" ? "border-orange-300 bg-orange-50/40" : ""}`}>
                <CardContent className="p-5">
                  <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500"><Badge variant="outline">{item.category}</Badge><span>{item.itemCode}</span><span>위험 가중치 {item.riskWeight}</span></div>
                  <p className="mt-3 font-semibold leading-7 text-gray-950"><span className="mr-2 text-blue-700">{index + 1}.</span>{item.question}</p>
                  <p className="mt-1 text-sm text-gray-600">작업: {item.workType}</p>
                  {item.description && <details className="mt-3 text-sm text-gray-600"><summary className="cursor-pointer font-medium text-blue-700">관련 재해 사례 보기</summary><p className="mt-2 rounded-md bg-gray-50 p-3 leading-6">{item.description}</p></details>}
                  <div className="mt-4 grid grid-cols-3 gap-2" role="group" aria-label={`${item.itemCode} 응답`}><AnswerButton active={answers[item.itemCode] === "YES"} onClick={() => setAnswer(item.itemCode, "YES")} label="예" tone="green" /><AnswerButton active={answers[item.itemCode] === "NO"} onClick={() => setAnswer(item.itemCode, "NO")} label="아니오" tone="red" /><AnswerButton active={answers[item.itemCode] === "NA"} onClick={() => setAnswer(item.itemCode, "NA")} label="해당 없음" tone="gray" /></div>
                </CardContent>
              </Card>
            ))}
          </div>

          <div className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Button variant="outline" size="lg" onClick={() => navigate("/")} className="h-12 sm:w-40">이전 단계</Button>
            {!riskAssessment && <Button size="lg" onClick={submitChecklist} disabled={isSubmitting || answeredCount !== personalizedItems.length} className="h-12 flex-1 bg-blue-600 hover:bg-blue-700">{isSubmitting ? <Loader2 className="h-5 w-5 animate-spin" /> : <CheckCircle2 className="h-5 w-5" />}체크리스트 제출하고 위험도 진단</Button>}
          </div>
        </>
      )}
    </div>
  );
}

function AnswerButton({ active, onClick, label, tone }: { active: boolean; onClick: () => void; label: string; tone: "green" | "red" | "gray" }) {
  const activeClass = tone === "green" ? "border-green-600 bg-green-600 text-white" : tone === "red" ? "border-red-600 bg-red-600 text-white" : "border-gray-700 bg-gray-700 text-white";
  return <Button type="button" variant="outline" onClick={onClick} aria-pressed={active} className={`min-h-11 whitespace-normal px-2 ${active ? activeClass : ""}`}>{label}</Button>;
}
