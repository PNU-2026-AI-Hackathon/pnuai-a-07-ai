import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { ArrowRight, Info, Lightbulb, Loader2, Search, ShieldCheck, Tags } from "lucide-react";
import { useSafety } from "../contexts/SafetyContext";
import type { SimilarCaseResponse } from "../types/safety";
import { safetyApi } from "../utils/api";
import { previewCases } from "../utils/devPreview";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";

export default function Step3Cases() {
  const navigate = useNavigate();
  const { workplace, riskAssessment } = useSafety();
  const [data, setData] = useState<SimilarCaseResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
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
      setData(previewCases);
      setIsLoading(false);
      return;
    }
    let active = true;
    setIsLoading(true);
    safetyApi.getSimilarCases(workplace.id, 5)
      .then((response) => { if (active) setData(response); })
      .catch((caught) => { if (active) setError(caught instanceof Error ? caught.message : "유사 사례를 불러오지 못했습니다."); })
      .finally(() => { if (active) setIsLoading(false); });
    return () => { active = false; };
  }, [workplace, riskAssessment, isPreview, navigate]);

  if (!workplace || !riskAssessment) return null;

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <header className="mb-8 text-center">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-orange-100 px-4 py-2 text-orange-800"><Search className="h-5 w-5" /><span className="text-sm font-medium">STEP 3 / 4 · 유사 재해사례</span></div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">비슷한 사업장의 사고를 확인하세요</h1>
        <p className="mt-3 text-gray-600">사업장 세부정보와 체크리스트 진단 결과를 기준으로 관련 중대재해를 찾았습니다.</p>
        {isPreview && <p className="mt-2 text-sm text-gray-500">예시 데이터로 표시한 화면입니다.</p>}
      </header>

      {isLoading ? (
        <Card><CardContent className="flex items-center justify-center gap-3 py-16 text-gray-600"><Loader2 className="h-6 w-6 animate-spin text-blue-600" />ML 유사 사례를 검색하는 중입니다...</CardContent></Card>
      ) : error ? (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-900">{error}</div>
      ) : !data || data.cases.length === 0 ? (
        <Card className="border-yellow-200"><CardContent className="flex flex-col items-center gap-3 py-12 text-center"><Info className="h-8 w-8 text-yellow-700" /><h2 className="font-semibold text-gray-950">표시할 유사 사례가 없습니다</h2><p className="max-w-xl text-sm leading-6 text-gray-600">{data?.note || "ML 서버가 인덱스를 준비 중일 수 있습니다. 잠시 후 다시 확인해 주세요."}</p></CardContent></Card>
      ) : (
        <>
          {data.recommendationBasis && <div className="mb-5 flex gap-3 rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm leading-6 text-blue-900"><ShieldCheck className="mt-0.5 h-5 w-5 shrink-0" /><p><strong>추천 근거:</strong> {data.recommendationBasis}</p></div>}
          {data.topKeywords.length > 0 && <div className="mb-6 flex flex-wrap items-center justify-center gap-2"><Tags className="h-4 w-4 text-blue-600" />{data.topKeywords.map((keyword) => <Badge key={keyword} variant="outline">{keyword}</Badge>)}</div>}
          <div className="space-y-5">
            {data.cases.map((item, index) => (
              <Card key={item.sifId} className="border-2">
                <CardHeader>
                  <div className="flex flex-wrap items-start justify-between gap-3"><div><Badge className="mb-2 bg-orange-600">유사 사례 {index + 1}</Badge><CardTitle className="text-lg">중대재해 사례 #{item.sifId}</CardTitle></div>{item.score !== null && <CardDescription>유사도 {(item.score * 100).toFixed(1)}%</CardDescription>}</div>
                </CardHeader>
                <CardContent className="space-y-4">
                  <p className="leading-7 text-gray-700">{item.summary}</p>
                  <div className="border-t border-gray-200 pt-4"><h3 className="mb-3 flex items-center gap-2 font-semibold text-gray-950"><Lightbulb className="h-5 w-5 text-green-600" />재발 방지 대책</h3>{item.countermeasures.length ? <ul className="space-y-2">{item.countermeasures.map((measure) => <li key={measure} className="flex gap-2 text-sm leading-6 text-gray-700"><span className="text-green-700">✓</span>{measure}</li>)}</ul> : <p className="text-sm text-gray-500">등록된 재발 방지 대책이 없습니다.</p>}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        </>
      )}

      <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
        <Button variant="outline" size="lg" onClick={() => navigate("/checklist")} className="h-12">이전 단계</Button>
        <Button size="lg" onClick={() => navigate("/prevention")} className="h-12 bg-blue-600 hover:bg-blue-700">맞춤 예방가이드 확인<ArrowRight className="h-5 w-5" /></Button>
      </div>
    </div>
  );
}
