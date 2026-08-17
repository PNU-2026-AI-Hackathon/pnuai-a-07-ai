import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { AlertTriangle, CheckCircle2, Download, Info, Loader2, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { useSafety } from "../contexts/SafetyContext";
import { safetyApi } from "../utils/api";
import { previewPreventionGuide } from "../utils/devPreview";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";

export default function Step2Report() {
  const navigate = useNavigate();
  const { workplace, riskAssessment, preventionGuide, setPreventionGuide } = useSafety();
  const [isLoading, setIsLoading] = useState(true);
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState("");
  const isPreview = import.meta.env.DEV && workplace?.id === 0;

  useEffect(() => {
    if (!workplace) {
      navigate("/", { replace: true });
      return;
    }
    if (!riskAssessment) {
      navigate("/checklist", { replace: true });
      return;
    }
    if (isPreview) {
      setPreventionGuide(previewPreventionGuide);
      setIsLoading(false);
      return;
    }

    let active = true;
    setIsLoading(true);
    safetyApi.getDiagnosisPreventionGuide(workplace.id)
      .then((guide) => { if (active) setPreventionGuide(guide); })
      .catch((caught) => { if (active) setError(caught instanceof Error ? caught.message : "예방가이드를 불러오지 못했습니다."); })
      .finally(() => { if (active) setIsLoading(false); });
    return () => { active = false; };
    // Context setter identity may change when another diagnosis field updates.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workplace, riskAssessment, isPreview, navigate]);

  if (!workplace || !riskAssessment) return null;

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
      toast.success("종합 안전진단 리포트를 다운로드했습니다.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "PDF 리포트를 만들지 못했습니다.");
    } finally {
      setIsDownloading(false);
    }
  };

  const predictions = preventionGuide?.predictions ?? [];

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <header className="mb-8 text-center">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-green-100 px-4 py-2 text-green-800">
          <ShieldCheck className="h-5 w-5" />
          <span className="text-sm font-medium">STEP 4 / 4 · 맞춤 예방가이드</span>
        </div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">미비 항목부터 개선하세요</h1>
        <p className="mt-3 text-gray-600">체크리스트 진단에서 확인된 위험요인을 우선순위대로 정리했습니다.</p>
        {isPreview && <p className="mt-2 text-sm text-gray-500">예시 데이터로 표시한 화면입니다.</p>}
      </header>

      <Card className="mb-6 border-2 border-blue-200">
        <CardContent className="grid gap-4 p-5 sm:grid-cols-3">
          <div><p className="text-sm text-gray-500">사업장</p><p className="mt-1 font-semibold text-gray-950">{workplace.name}</p></div>
          <div><p className="text-sm text-gray-500">최종 위험도</p><p className="mt-1 font-semibold text-gray-950">{riskAssessment.riskScore === null ? "산정 불가" : `${riskAssessment.riskScore.toFixed(1)}점`}</p></div>
          <div><p className="text-sm text-gray-500">최우선 재해유형</p><p className="mt-1 font-semibold text-orange-800">{riskAssessment.topRisks?.[0]?.type ?? riskAssessment.topAccidentType ?? "미비 위험 없음"}</p></div>
        </CardContent>
      </Card>

      {isLoading ? (
        <Card><CardContent className="flex items-center justify-center gap-3 py-16 text-gray-600"><Loader2 className="h-6 w-6 animate-spin text-blue-600" />진단 결과로 예방가이드를 만드는 중입니다...</CardContent></Card>
      ) : error ? (
        <div role="alert" className="flex gap-3 rounded-md border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-900"><AlertTriangle className="h-5 w-5 shrink-0" /><p>{error}</p></div>
      ) : predictions.length === 0 ? (
        <Card className="border-green-200">
          <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
            <CheckCircle2 className="h-9 w-9 text-green-700" />
            <h2 className="text-xl font-semibold text-gray-950">중대한 미비 항목이 없습니다</h2>
            <p className="max-w-xl text-sm leading-6 text-gray-600">현재 상태를 유지하고 정기점검을 반복하세요. 현장 조건이 바뀌면 체크리스트를 다시 진행하는 것이 좋습니다.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-6">
          {predictions.map((prediction) => (
            <Card key={`${prediction.rank}-${prediction.accidentType}`} className="border-2">
              <CardHeader>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <Badge className="mb-2 bg-orange-600">개선 우선순위 {prediction.rank}</Badge>
                    <CardTitle className="text-xl">{prediction.accidentType} 예방조치</CardTitle>
                    <CardDescription className="mt-1">체크리스트에서 확인된 미비 항목 {prediction.checklist.length}개</CardDescription>
                  </div>
                  <div className="text-right"><p className="text-2xl font-bold text-orange-700">{(prediction.ratio * 100).toFixed(1)}%</p><p className="text-xs text-gray-500">미비 위험 비중</p></div>
                </div>
              </CardHeader>
              <CardContent>
                <ul className="divide-y divide-gray-200">
                  {prediction.checklist.map((item) => (
                    <li key={item.itemCode} className="py-4 first:pt-0 last:pb-0">
                      <div className="flex flex-wrap items-center gap-2"><Badge variant="outline">{item.workType || "공통 작업"}</Badge>{item.isCritical && <Badge className="bg-red-700">중대 항목</Badge>}<span className="text-xs text-gray-500">위험 가중치 {item.riskWeight}</span></div>
                      <p className="mt-2 font-medium leading-7 text-gray-950">{item.question}</p>
                      {item.lawBasis.length > 0 ? <p className="mt-2 text-sm text-blue-800">근거: {item.lawBasis.join(", ")}</p> : <p className="mt-2 flex items-center gap-2 text-sm text-gray-500"><Info className="h-4 w-4" />연결된 법령 근거를 확인 중입니다.</p>}
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
        <Button variant="outline" size="lg" onClick={() => navigate("/cases")} className="h-12">이전 단계</Button>
        <Button size="lg" onClick={downloadReport} disabled={isDownloading || isLoading} className="h-12 bg-blue-600 hover:bg-blue-700">{isDownloading ? <Loader2 className="h-5 w-5 animate-spin" /> : <Download className="h-5 w-5" />}종합 PDF 리포트 다운로드</Button>
      </div>
    </div>
  );
}
