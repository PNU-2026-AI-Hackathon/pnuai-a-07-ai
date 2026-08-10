import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { AlertCircle, CheckCircle2, Download, FileText, Loader2, RefreshCw, ShieldAlert } from "lucide-react";
import { toast } from "sonner";
import { useSafety } from "../contexts/SafetyContext";
import type { Answer, RiskGrade } from "../types/safety";
import { ApiError, safetyApi } from "../utils/api";
import { previewRiskAssessment } from "../utils/devPreview";
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

export default function Step4Checklist() {
  const navigate = useNavigate();
  const { workplace, checklistItems, setChecklistItems, checklistAnswers: answers, setChecklistAnswers, riskAssessment, setRiskAssessment } = useSafety();
  const [category, setCategory] = useState("ALL");
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState("");
  const isPreview = import.meta.env.DEV && workplace?.id === 0;

  const loadChecklist = async () => {
    if (!workplace) return;
    setIsLoading(true);
    setError("");
    try {
      const items = await safetyApi.getChecklistItems(workplace.id, true);
      setChecklistItems(items);
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
    if (checklistItems.length === 0) void loadChecklist();
    // 기존 진단 세션을 재사용하고, 최초 진입 때만 서버 문항을 조회합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workplace?.id]);

  const categories = useMemo(() => Array.from(new Set(checklistItems.map((item) => item.category))).sort(), [checklistItems]);
  const visibleItems = category === "ALL" ? checklistItems : checklistItems.filter((item) => item.category === category);
  const answeredCount = Object.keys(answers).filter((code) => checklistItems.some((item) => item.itemCode === code)).length;
  const completionRate = checklistItems.length ? Math.round((answeredCount / checklistItems.length) * 100) : 0;

  if (!workplace) return null;

  const setAnswer = (itemCode: string, answer: Answer) => {
    setChecklistAnswers({ ...answers, [itemCode]: answer });
    setError("");
  };

  const submitChecklist = async () => {
    if (answeredCount !== checklistItems.length) {
      setError(`모든 중대 항목에 답해 주세요. 아직 ${checklistItems.length - answeredCount}개가 남았습니다.`);
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
        checklistItems.map((item) => ({ itemCode: item.itemCode, answer: answers[item.itemCode] })),
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
      if (report.status !== "DONE") {
        throw new Error(`리포트 상태가 ${report.status}입니다. 잠시 후 다시 시도해 주세요.`);
      }
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
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">현장 상태를 점검하세요</h1>
        <p className="mt-3 text-gray-600">{workplace.name}의 중대 항목만 우선 불러옵니다. 해당 없음은 위험도 계산에서 제외됩니다.</p>
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
              <div className="flex h-5 overflow-hidden rounded-full bg-gray-100" aria-label={`기본 위험 ${base.toFixed(1)}점, 체크리스트 위험 ${checklist.toFixed(1)}점`}>
                <div className="bg-blue-600" style={{ width: `${base}%` }} />
                <div className="bg-orange-600" style={{ width: `${checklist}%` }} />
              </div>
              <div className="mt-2 flex flex-wrap gap-4 text-xs text-gray-600"><span>● <span className="text-blue-700">기본 위험</span></span><span>● <span className="text-orange-700">미비 항목 위험</span></span></div>
            </div>
            <div className="grid gap-3 rounded-md bg-gray-50 p-4 text-sm sm:grid-cols-3">
              <p><span className="text-gray-500">최우선 재해유형</span><br /><strong>{riskAssessment.topAccidentType ?? "정보 없음"}</strong></p>
              <p><span className="text-gray-500">진단 방식</span><br /><strong>{riskAssessment.method}</strong></p>
              <p><span className="text-gray-500">통계 매칭</span><br /><strong>{riskAssessment.matchLevel ?? "정보 없음"}</strong></p>
            </div>
            {(riskAssessment.topRisks ?? []).length > 0 ? (
              <div className="grid gap-4 md:grid-cols-2">
                <section aria-labelledby="ml-risk-title" className="rounded-md border border-orange-200 bg-orange-50 p-4">
                  <h3 id="ml-risk-title" className="font-semibold text-gray-950">ML 예상 재해유형</h3>
                  <ul className="mt-3 space-y-2">{(riskAssessment.topRisks ?? []).slice(0, 3).map((item) => <li key={item.type} className="flex items-center justify-between gap-3 text-sm"><span>{item.type}</span><strong className="text-orange-800">{(item.probability * 100).toFixed(1)}%</strong></li>)}</ul>
                </section>
                <section aria-labelledby="ml-severity-title" className="rounded-md border border-blue-200 bg-blue-50 p-4">
                  <h3 id="ml-severity-title" className="font-semibold text-gray-950">예상 피해 심각도</h3>
                  {(riskAssessment.severityPrediction ?? []).length ? <ul className="mt-3 space-y-2">{(riskAssessment.severityPrediction ?? []).slice(0, 3).map((item) => <li key={item.label} className="flex items-center justify-between gap-3 text-sm"><span>{item.label}</span><strong className="text-blue-800">{(item.probability * 100).toFixed(1)}%</strong></li>)}</ul> : <p className="mt-3 text-sm text-gray-600">심각도 예측 데이터가 없습니다.</p>}
                </section>
              </div>
            ) : <p className="rounded-md bg-gray-50 px-4 py-3 text-sm text-gray-600">ML 서버를 사용할 수 없어 통계 기반 점수만 표시합니다.</p>}
            {riskAssessment.matchLevel && riskAssessment.matchLevel !== "EXACT" && <div className="flex gap-2 rounded-md border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-900"><ShieldAlert className="h-5 w-5 shrink-0" />유사 사업장 데이터가 적어 이 결과는 참고치로 활용해 주세요.</div>}
            <Button onClick={downloadReport} disabled={isDownloading} size="lg" className="h-12 w-full bg-blue-600 hover:bg-blue-700">{isDownloading ? <Loader2 className="h-5 w-5 animate-spin" /> : <Download className="h-5 w-5" />}PDF 진단 리포트 다운로드</Button>
          </CardContent>
        </Card>
      )}

      {isLoading ? (
        <Card><CardContent className="flex items-center justify-center gap-3 py-16 text-gray-600"><Loader2 className="h-6 w-6 animate-spin text-blue-600" />중대 문항을 불러오는 중입니다...</CardContent></Card>
      ) : checklistItems.length === 0 ? (
        <Card><CardContent className="flex flex-col items-center gap-4 py-12 text-center"><FileText className="h-8 w-8 text-gray-400" /><p className="text-gray-600">등록된 중대 체크리스트 문항이 없습니다.</p><Button variant="outline" onClick={loadChecklist}><RefreshCw className="h-4 w-4" />다시 불러오기</Button></CardContent></Card>
      ) : (
        <>
          <Card className="mb-6 border-2">
            <CardContent className="p-5">
              <div className="mb-3 flex flex-wrap items-end justify-between gap-3"><div><h2 className="font-semibold text-gray-950">응답 진행률</h2><p className="text-sm text-gray-600">{answeredCount} / {checklistItems.length}개 응답</p></div><strong className="text-2xl text-blue-700">{completionRate}%</strong></div>
              <Progress value={completionRate} className="h-3" />
              <div className="mt-5 max-w-xs"><Select value={category} onValueChange={setCategory}><SelectTrigger aria-label="재해유형 필터"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="ALL">전체 재해유형 ({checklistItems.length})</SelectItem>{categories.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div>
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
                  <div className="mt-4 grid grid-cols-3 gap-2" role="group" aria-label={`${item.itemCode} 응답`}>
                    <AnswerButton active={answers[item.itemCode] === "YES"} onClick={() => setAnswer(item.itemCode, "YES")} label="예" tone="green" />
                    <AnswerButton active={answers[item.itemCode] === "NO"} onClick={() => setAnswer(item.itemCode, "NO")} label="아니오" tone="red" />
                    <AnswerButton active={answers[item.itemCode] === "NA"} onClick={() => setAnswer(item.itemCode, "NA")} label="해당 없음" tone="gray" />
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          {error && <div role="alert" className="mt-6 flex gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-red-900"><AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" /><p className="text-sm">{error}</p></div>}

          <div className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Button variant="outline" size="lg" onClick={() => navigate("/cases")} className="h-12 sm:w-40">이전 단계</Button>
            <Button size="lg" onClick={submitChecklist} disabled={isSubmitting || answeredCount !== checklistItems.length} className="h-12 flex-1 bg-blue-600 hover:bg-blue-700">{isSubmitting ? <Loader2 className="h-5 w-5 animate-spin" /> : <CheckCircle2 className="h-5 w-5" />}체크리스트 제출하고 위험도 진단</Button>
          </div>
        </>
      )}

      {error && checklistItems.length === 0 && <div role="alert" className="mt-6 flex gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-red-900"><AlertCircle className="h-5 w-5 shrink-0" /><p className="text-sm">{error}</p></div>}
    </div>
  );
}

function AnswerButton({ active, onClick, label, tone }: { active: boolean; onClick: () => void; label: string; tone: "green" | "red" | "gray" }) {
  const activeClass = tone === "green" ? "border-green-600 bg-green-600 text-white" : tone === "red" ? "border-red-600 bg-red-600 text-white" : "border-gray-700 bg-gray-700 text-white";
  return <Button type="button" variant="outline" onClick={onClick} aria-pressed={active} className={`min-h-11 whitespace-normal px-2 ${active ? activeClass : ""}`}>{label}</Button>;
}
