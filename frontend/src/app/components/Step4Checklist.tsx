import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { AlertCircle, CheckCircle2, Download, FileText, Loader2, RefreshCw, ShieldAlert, SlidersHorizontal } from "lucide-react";
import { toast } from "sonner";
import { useSafety } from "../contexts/SafetyContext";
import type { Answer, RiskGrade, WorkTypeReference } from "../types/safety";
import { ApiError, safetyApi } from "../utils/api";
import { previewChecklistItems, previewRiskAssessment } from "../utils/devPreview";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Checkbox } from "./ui/checkbox";
import { Progress } from "./ui/progress";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";

const gradeConfig: Record<RiskGrade, { label: string; className: string }> = {
  LOW: { label: "양호", className: "bg-green-600" },
  MEDIUM: { label: "보통", className: "bg-yellow-600" },
  HIGH: { label: "위험", className: "bg-orange-600" },
  CRITICAL: { label: "매우 위험", className: "bg-red-700" },
};

const MAX_CHECKLIST_ITEMS = 30;

export default function Step4Checklist() {
  const navigate = useNavigate();
  const {
    workplace,
    preventionGuide,
    checklistItems,
    setChecklistItems,
    selectedWorkTypes,
    setSelectedWorkTypes,
    checklistAnswers: answers,
    setChecklistAnswers,
    riskAssessment,
    setRiskAssessment,
  } = useSafety();
  const [availableWorkTypes, setAvailableWorkTypes] = useState<WorkTypeReference[]>([]);
  const [draftWorkTypes, setDraftWorkTypes] = useState<string[]>(selectedWorkTypes);
  const [category, setCategory] = useState("ALL");
  const [isLoadingReferences, setIsLoadingReferences] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState("");
  const isPreview = import.meta.env.DEV && workplace?.id === 0;

  const suggestedWorkTypes = useMemo(() => Array.from(new Set(
    (preventionGuide?.predictions ?? [])
      .flatMap((prediction) => prediction.checklist)
      .map((item) => item.workType)
      .filter(Boolean),
  )), [preventionGuide]);

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
  const hasUnappliedChanges = [...draftWorkTypes].sort().join("|") !== [...selectedWorkTypes].sort().join("|");

  const loadChecklist = async (workTypes: string[]) => {
    if (!workplace || workTypes.length === 0) return;
    setIsLoading(true);
    setError("");
    try {
      if (isPreview) {
        const items = previewChecklistItems
          .filter((item) => workTypes.includes(item.workType))
          .slice(0, MAX_CHECKLIST_ITEMS);
        setChecklistItems(items);
      } else {
        const items = await safetyApi.getChecklistItems(workplace.id, true, workTypes, MAX_CHECKLIST_ITEMS);
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

    const loadReferences = async () => {
      setIsLoadingReferences(true);
      try {
        if (isPreview) {
          const counts = new Map<string, number>();
          checklistItems.forEach((item) => counts.set(item.workType, (counts.get(item.workType) ?? 0) + 1));
          setAvailableWorkTypes(Array.from(counts, ([workType, itemCount]) => ({ industry: workplace.industry, workType, itemCount })));
          return;
        }
        const references = await safetyApi.getReferences();
        const workTypes = references.workTypes.filter((item) => item.industry === workplace.industry);
        setAvailableWorkTypes(workTypes);
        if (selectedWorkTypes.length === 0 && draftWorkTypes.length === 0) {
          const recommended = suggestedWorkTypes.filter((workType) => workTypes.some((item) => item.workType === workType));
          setDraftWorkTypes(recommended.slice(0, 4));
        }
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : "작업유형을 불러오지 못했습니다.");
      } finally {
        setIsLoadingReferences(false);
      }
    };

    void loadReferences();
    if (selectedWorkTypes.length > 0 && checklistItems.length === 0) {
      void loadChecklist(selectedWorkTypes);
    } else if (selectedWorkTypes.length === 0 && checklistItems.length > 0 && !isPreview) {
      setChecklistItems([]);
    }
    // 진단 세션이 바뀌거나 체크리스트에 처음 진입할 때만 기준정보와 문항을 조회합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workplace?.id]);

  if (!workplace) return null;

  const toggleWorkType = (workType: string, checked: boolean) => {
    setDraftWorkTypes((current) => checked
      ? Array.from(new Set([...current, workType]))
      : current.filter((item) => item !== workType));
    setError("");
  };

  const applyWorkTypes = async () => {
    if (draftWorkTypes.length === 0) {
      setError("우리 현장에서 수행하는 작업을 하나 이상 선택해 주세요.");
      return;
    }
    setSelectedWorkTypes(draftWorkTypes);
    setChecklistAnswers({});
    setRiskAssessment(null);
    setCategory("ALL");
    setChecklistItems([]);
    await loadChecklist(draftWorkTypes);
  };

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

  const downloadReport = async () => {
    if (isPreview) {
      toast.info("예시 화면에서는 PDF 다운로드를 제공하지 않습니다.");
      return;
    }
    setIsDownloading(true);
    setError("");
    try {
      const report = await safetyApi.createReport(workplace.id);
      if (report.status !== "DONE") throw new Error(`리포트 상태가 ${report.status}입니다. 잠시 후 다시 시도해 주세요.`);
      await safetyApi.downloadReport(report.reportId);
      toast.success("PDF 리포트를 다운로드했습니다.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "PDF 리포트를 만들지 못했습니다.");
    } finally {
      setIsDownloading(false);
    }
  };

  const grade = riskAssessment?.riskGrade ? gradeConfig[riskAssessment.riskGrade] : null;
  const base = riskAssessment?.baseComponent ?? 0;
  const checklist = riskAssessment?.checklistComponent ?? 0;

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <header className="mb-8 text-center">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-red-100 px-4 py-2 text-red-800"><CheckCircle2 className="h-5 w-5" /><span className="text-sm font-medium">STEP 4 / 4 · 중대 체크리스트</span></div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">현장 작업에 맞춰 점검하세요</h1>
        <p className="mt-3 text-gray-600">해당 작업의 중대 문항만 위험도 순으로 최대 {MAX_CHECKLIST_ITEMS}개 불러옵니다. 해당 없음은 위험도 계산에서 제외됩니다.</p>
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
              <p><span className="text-gray-500">최우선 재해유형</span><br /><strong>{riskAssessment.topAccidentType ?? "정보 없음"}</strong></p>
              <p><span className="text-gray-500">진단 방식</span><br /><strong>{riskAssessment.method}</strong></p>
              <p><span className="text-gray-500">통계 매칭</span><br /><strong>{riskAssessment.matchLevel ?? "정보 없음"}</strong></p>
            </div>
            {(riskAssessment.topRisks ?? []).length > 0 ? (
              <div className="grid gap-4 md:grid-cols-2">
                <section aria-labelledby="ml-risk-title" className="rounded-md border border-orange-200 bg-orange-50 p-4"><h3 id="ml-risk-title" className="font-semibold text-gray-950">ML 예상 재해유형</h3><ul className="mt-3 space-y-2">{(riskAssessment.topRisks ?? []).slice(0, 3).map((item) => <li key={item.type} className="flex items-center justify-between gap-3 text-sm"><span>{item.type}</span><strong className="text-orange-800">{(item.probability * 100).toFixed(1)}%</strong></li>)}</ul></section>
                <section aria-labelledby="ml-severity-title" className="rounded-md border border-blue-200 bg-blue-50 p-4"><h3 id="ml-severity-title" className="font-semibold text-gray-950">예상 피해 심각도</h3>{(riskAssessment.severityPrediction ?? []).length ? <ul className="mt-3 space-y-2">{(riskAssessment.severityPrediction ?? []).slice(0, 3).map((item) => <li key={item.label} className="flex items-center justify-between gap-3 text-sm"><span>{item.label}</span><strong className="text-blue-800">{(item.probability * 100).toFixed(1)}%</strong></li>)}</ul> : <p className="mt-3 text-sm text-gray-600">심각도 예측 데이터가 없습니다.</p>}</section>
              </div>
            ) : <p className="rounded-md bg-gray-50 px-4 py-3 text-sm text-gray-600">ML 서버를 사용할 수 없어 통계 기반 점수만 표시합니다.</p>}
            {riskAssessment.matchLevel && riskAssessment.matchLevel !== "EXACT" && <div className="flex gap-2 rounded-md border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-900"><ShieldAlert className="h-5 w-5 shrink-0" />유사 사업장 데이터가 적어 이 결과는 참고치로 활용해 주세요.</div>}
            <Button onClick={downloadReport} disabled={isDownloading} size="lg" className="h-12 w-full bg-blue-600 hover:bg-blue-700">{isDownloading ? <Loader2 className="h-5 w-5 animate-spin" /> : <Download className="h-5 w-5" />}PDF 진단 리포트 다운로드</Button>
          </CardContent>
        </Card>
      )}

      <Card className="mb-6 border-2 border-blue-200">
        <CardHeader>
          <div className="flex items-start gap-3"><SlidersHorizontal className="mt-0.5 h-5 w-5 shrink-0 text-blue-700" /><div><CardTitle className="text-xl">우리 현장 작업을 선택하세요</CardTitle><CardDescription className="mt-1">선택한 작업과 관련 없는 문항은 처음부터 제외합니다.</CardDescription></div></div>
        </CardHeader>
        <CardContent className="space-y-5">
          {isLoadingReferences ? <div className="flex items-center gap-2 text-sm text-gray-600"><Loader2 className="h-4 w-4 animate-spin text-blue-600" />작업유형을 불러오는 중입니다...</div> : availableWorkTypes.length === 0 ? <p className="text-sm text-gray-600">이 업종에 등록된 작업유형이 없습니다.</p> : (
            <>
              {suggestedWorkTypes.some((workType) => availableWorkTypes.some((item) => item.workType === workType)) && (
                <section aria-labelledby="recommended-work-title">
                  <h2 id="recommended-work-title" className="text-sm font-semibold text-blue-900">위험 리포트 기반 추천 작업</h2>
                  <div className="mt-2 grid gap-2 sm:grid-cols-2">
                    {availableWorkTypes.filter((item) => suggestedWorkTypes.includes(item.workType)).slice(0, 4).map((item) => <WorkTypeOption key={item.workType} item={item} checked={draftWorkTypes.includes(item.workType)} recommended onChange={toggleWorkType} />)}
                  </div>
                </section>
              )}
              <details open={suggestedWorkTypes.length === 0}>
                <summary className="cursor-pointer text-sm font-semibold text-gray-800">전체 작업유형에서 선택</summary>
                <div className="mt-3 grid max-h-72 gap-2 overflow-y-auto pr-1 sm:grid-cols-2">
                  {availableWorkTypes.map((item) => <WorkTypeOption key={item.workType} item={item} checked={draftWorkTypes.includes(item.workType)} onChange={toggleWorkType} />)}
                </div>
              </details>
            </>
          )}
          <div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-gray-600"><strong className="text-gray-950">{draftWorkTypes.length}개 작업</strong> 선택{selectedWorkTypes.length > 0 && !hasUnappliedChanges ? " · 현재 문항에 적용됨" : ""}</p>
            <Button onClick={applyWorkTypes} disabled={isLoading || isLoadingReferences || draftWorkTypes.length === 0 || (!hasUnappliedChanges && checklistItems.length > 0)} variant={checklistItems.length > 0 ? "outline" : "default"} className={checklistItems.length > 0 ? "" : "bg-blue-600 hover:bg-blue-700"}>{isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}{checklistItems.length > 0 ? "선택 작업 다시 적용" : "선택 작업 문항 불러오기"}</Button>
          </div>
        </CardContent>
      </Card>

      {error && <div role="alert" className="mb-6 flex gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-red-900"><AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" /><p className="text-sm">{error}</p></div>}

      {isLoading ? (
        <Card><CardContent className="flex items-center justify-center gap-3 py-16 text-gray-600"><Loader2 className="h-6 w-6 animate-spin text-blue-600" />선택한 작업의 중대 문항을 불러오는 중입니다...</CardContent></Card>
      ) : selectedWorkTypes.length === 0 ? (
        <Card><CardContent className="flex flex-col items-center gap-3 py-12 text-center"><FileText className="h-8 w-8 text-gray-400" /><p className="font-medium text-gray-900">현장 작업을 먼저 선택해 주세요.</p><p className="text-sm text-gray-600">선택 전에는 체크리스트 문항을 표시하지 않습니다.</p></CardContent></Card>
      ) : personalizedItems.length === 0 ? (
        <Card><CardContent className="flex flex-col items-center gap-4 py-12 text-center"><FileText className="h-8 w-8 text-gray-400" /><p className="text-gray-600">선택한 작업에 등록된 중대 문항이 없습니다.</p><Button variant="outline" onClick={() => loadChecklist(selectedWorkTypes)}><RefreshCw className="h-4 w-4" />다시 불러오기</Button></CardContent></Card>
      ) : (
        <>
          <Card className="mb-6 border-2">
            <CardContent className="p-5">
              <div className="mb-3 flex flex-wrap items-end justify-between gap-3"><div><h2 className="font-semibold text-gray-950">맞춤 문항 응답 진행률</h2><p className="text-sm text-gray-600">{answeredCount} / {personalizedItems.length}개 응답 · 선택한 작업 {selectedWorkTypes.length}개 기준</p></div><strong className="text-2xl text-blue-700">{completionRate}%</strong></div>
              <Progress value={completionRate} className="h-3" />
              <div className="mt-4 flex flex-wrap gap-2">{selectedWorkTypes.map((workType) => <Badge key={workType} variant="secondary">{workType}</Badge>)}</div>
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
            <Button variant="outline" size="lg" onClick={() => navigate("/cases")} className="h-12 sm:w-40">이전 단계</Button>
            <Button size="lg" onClick={submitChecklist} disabled={isSubmitting || answeredCount !== personalizedItems.length || hasUnappliedChanges} className="h-12 flex-1 bg-blue-600 hover:bg-blue-700">{isSubmitting ? <Loader2 className="h-5 w-5 animate-spin" /> : <CheckCircle2 className="h-5 w-5" />}체크리스트 제출하고 위험도 진단</Button>
          </div>
          {hasUnappliedChanges && <p className="mt-3 text-center text-sm text-orange-700">작업 선택을 변경했습니다. 문항에 다시 적용한 후 제출할 수 있습니다.</p>}
        </>
      )}
    </div>
  );
}

function WorkTypeOption({ item, checked, recommended = false, onChange }: { item: WorkTypeReference; checked: boolean; recommended?: boolean; onChange: (workType: string, checked: boolean) => void }) {
  const id = `work-type-${recommended ? "recommended" : "all"}-${item.workType}`;
  return (
    <label htmlFor={id} className={`flex min-h-12 cursor-pointer items-center gap-3 rounded-md border px-3 py-2 text-sm ${checked ? "border-blue-500 bg-blue-50" : "border-gray-200 bg-white"}`}>
      <Checkbox id={id} checked={checked} onCheckedChange={(value) => onChange(item.workType, value === true)} />
      <span className="min-w-0 flex-1 font-medium text-gray-900">{item.workType}</span>
      <span className="shrink-0 text-xs text-gray-500">{item.itemCount}문항</span>
      {recommended && <Badge className="shrink-0 bg-blue-600">추천</Badge>}
    </label>
  );
}

function AnswerButton({ active, onClick, label, tone }: { active: boolean; onClick: () => void; label: string; tone: "green" | "red" | "gray" }) {
  const activeClass = tone === "green" ? "border-green-600 bg-green-600 text-white" : tone === "red" ? "border-red-600 bg-red-600 text-white" : "border-gray-700 bg-gray-700 text-white";
  return <Button type="button" variant="outline" onClick={onClick} aria-pressed={active} className={`min-h-11 whitespace-normal px-2 ${active ? activeClass : ""}`}>{label}</Button>;
}
