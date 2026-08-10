import { useEffect } from "react";
import { useNavigate } from "react-router";
import { AlertTriangle, ArrowRight, BarChart3, Info, ShieldCheck } from "lucide-react";
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { useSafety } from "../contexts/SafetyContext";
import { previewPreventionGuide, previewWorkplace } from "../utils/devPreview";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";

const COLORS = ["#ea580c", "#ca8a04", "#2563eb"];

export default function Step2Report() {
  const navigate = useNavigate();
  const { workplace, preventionGuide, startPreviewDiagnosis } = useSafety();
  const isPreview = import.meta.env.DEV && (!workplace || !preventionGuide);
  const reportWorkplace = workplace ?? (isPreview ? previewWorkplace : null);
  const reportPreventionGuide = preventionGuide ?? (isPreview ? previewPreventionGuide : null);

  useEffect(() => {
    if (isPreview) startPreviewDiagnosis();
    if (!reportWorkplace || !reportPreventionGuide) navigate("/", { replace: true });
  }, [isPreview, reportWorkplace, reportPreventionGuide, startPreviewDiagnosis, navigate]);

  if (!reportWorkplace || !reportPreventionGuide) return null;

  const predictions = reportPreventionGuide.predictions ?? [];
  const chartData = predictions.map((item) => ({ name: item.accidentType, value: Number((item.ratio * 100).toFixed(1)) }));

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <header className="mb-8 text-center">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-orange-100 px-4 py-2 text-orange-800">
          <AlertTriangle className="h-5 w-5" /><span className="text-sm font-medium">STEP 2 / 4 · 예방 가이드</span>
        </div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">예상 재해유형을 확인하세요</h1>
        <p className="mt-3 text-gray-600">{reportWorkplace.name} · {reportWorkplace.industry}{reportWorkplace.subIndustry ? ` · ${reportWorkplace.subIndustry}` : ""} · {reportWorkplace.region}</p>
        {isPreview && <p className="mt-2 text-sm text-gray-500">예시 데이터로 표시한 화면입니다.</p>}
      </header>

      {predictions.length === 0 ? (
        <Card className="border-orange-200">
          <CardContent className="flex flex-col items-center gap-3 px-6 py-12 text-center">
            <Info className="h-8 w-8 text-orange-600" />
            <h2 className="text-xl font-semibold">예방 가이드 데이터가 없습니다</h2>
            <p className="max-w-lg text-gray-600">DB 스키마가 최신인지 확인해 주세요. 사업장은 등록되었으므로 잠시 후 다시 시도할 수 있습니다.</p>
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="mb-8 grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
            <Card className="border-2">
              <CardHeader><CardTitle className="flex items-center gap-2 text-xl"><BarChart3 className="h-5 w-5 text-blue-600" />예상 재해 비중</CardTitle><CardDescription>통계 기반 예방 가이드 결과입니다.</CardDescription></CardHeader>
              <CardContent>
                <div className="h-[280px]" aria-label="예상 재해유형 비중 차트">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={chartData} dataKey="value" nameKey="name" outerRadius={88} label={({ value }) => `${value}%`}>
                        {chartData.map((item, index) => <Cell key={item.name} fill={COLORS[index % COLORS.length]} />)}
                      </Pie>
                      <Tooltip formatter={(value) => [`${value}%`, "예상 비중"]} />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              </CardContent>
            </Card>

            <div className="space-y-4">
              {predictions.map((prediction) => (
                <Card key={`${prediction.rank}-${prediction.accidentType}`} className="border-2">
                  <CardContent className="p-5">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div><Badge variant="outline" className="mb-2">예상 {prediction.rank}순위</Badge><h2 className="text-xl font-bold text-gray-950">{prediction.accidentType}</h2></div>
                      <div className="text-right"><p className="text-2xl font-bold text-orange-700">{(prediction.ratio * 100).toFixed(1)}%</p><p className="text-xs text-gray-500">사망 비중 {(prediction.deathRatio * 100).toFixed(1)}%</p></div>
                    </div>
                    <div className="mt-4 flex items-center gap-2 text-sm text-gray-600"><ShieldCheck className="h-4 w-4 text-green-600" />연결된 예방 항목 {prediction.checklist.length}개</div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>

          <div className="mb-8 rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900">
            이 화면은 체크리스트 제출 전의 <strong>예상 재해유형</strong>입니다. 사업장 위험 점수는 체크리스트 제출 후 계산됩니다.
          </div>
        </>
      )}

      <div className="flex flex-col justify-center gap-3 sm:flex-row">
        <Button variant="outline" size="lg" onClick={() => navigate("/")} className="h-12">사업장 다시 입력</Button>
        <Button size="lg" onClick={() => navigate("/cases")} className="h-12 bg-blue-600 hover:bg-blue-700">예방 조치 자세히 보기<ArrowRight className="h-5 w-5" /></Button>
      </div>
    </div>
  );
}
