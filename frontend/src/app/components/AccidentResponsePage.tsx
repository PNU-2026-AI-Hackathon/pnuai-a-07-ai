import { FormEvent, useState } from "react";
import { AlertTriangle, CheckCircle2, Clock3, FileWarning, Gavel, Info, Loader2, Search, ShieldAlert, Stethoscope } from "lucide-react";
import { useSafety } from "../contexts/SafetyContext";
import type { AccidentConsultResponse, AccidentResponseGuide, GuidanceSection, ImmediateAction } from "../types/safety";
import { safetyApi } from "../utils/api";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Label } from "./ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./ui/tabs";
import { Textarea } from "./ui/textarea";

const industries = ["제조업", "건설업", "운수창고통신업", "전기가스증기수도사업"];
const commonAccidentTypes = ["끼임", "떨어짐", "넘어짐", "물체에맞음", "부딪힘", "화재", "감전", "질식"];

export default function AccidentResponsePage() {
  const { workplace } = useSafety();
  const [situation, setSituation] = useState("");
  const [industry, setIndustry] = useState(workplace?.industry || "제조업");
  const [consultResult, setConsultResult] = useState<AccidentConsultResponse | null>(null);
  const [previewType, setPreviewType] = useState("");
  const [previewResult, setPreviewResult] = useState<AccidentResponseGuide | null>(null);
  const [loading, setLoading] = useState<"consult" | "preview" | null>(null);
  const [error, setError] = useState("");

  const consult = async (event?: FormEvent, correctedType?: string) => {
    event?.preventDefault();
    if (situation.trim().length < 10) { setError("사고 상황을 10자 이상 구체적으로 적어 주세요."); return; }
    setLoading("consult"); setError("");
    try { setConsultResult(await safetyApi.consultAccident({ situation: situation.trim(), industry, accidentType: correctedType })); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "사고 대처 안내를 만들지 못했습니다."); }
    finally { setLoading(null); }
  };

  const preview = async (event: FormEvent) => {
    event.preventDefault();
    if (!previewType) { setError("미리 볼 재해유형을 선택해 주세요."); return; }
    setLoading("preview"); setError("");
    try { setPreviewResult(await safetyApi.getAccidentResponse(previewType, industry)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "유형별 대처 안내를 불러오지 못했습니다."); }
    finally { setLoading(null); }
  };

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <header className="mb-8">
        <div className="mb-3 inline-flex items-center gap-2 rounded-full bg-red-100 px-4 py-2 text-sm font-medium text-red-800"><AlertTriangle className="h-4 w-4" />실제 사고 대처</div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">사고 내용을 그대로 적어 주세요</h1>
        <p className="mt-3 text-gray-600">즉시 조치, 법적 의무, 행정 처리와 위반 위험을 근거 법령과 함께 안내합니다.</p>
      </header>

      <Tabs defaultValue="consult">
        <TabsList className="mb-6 grid h-11 w-full max-w-md grid-cols-2"><TabsTrigger value="consult">사고 상황 입력</TabsTrigger><TabsTrigger value="preview">유형별 미리보기</TabsTrigger></TabsList>
        <TabsContent value="consult" className="m-0 space-y-6">
          <Card className="border-2 border-red-200"><CardHeader><CardTitle className="flex items-center gap-2 text-xl"><Stethoscope className="h-5 w-5 text-red-600" />현재 상황</CardTitle><CardDescription>발생 시각, 장소, 부상 정도, 현재 조치를 포함하면 더 정확합니다.</CardDescription></CardHeader><CardContent><form onSubmit={(event) => consult(event)} className="space-y-5"><div className="space-y-2"><Label htmlFor="accident-industry">업종</Label><Select value={industry} onValueChange={setIndustry}><SelectTrigger id="accident-industry"><SelectValue /></SelectTrigger><SelectContent>{industries.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div><div className="space-y-2"><Label htmlFor="accident-situation">사고 내용</Label><Textarea id="accident-situation" value={situation} onChange={(event) => setSituation(event.target.value)} maxLength={2000} className="min-h-40" placeholder="예: 오늘 오후 공장에서 직원의 손이 프레스 기계에 끼어 손가락을 다쳤습니다. 119에 신고해 현재 병원으로 이송했습니다." /><p className="text-right text-xs text-gray-500">{situation.length}/2000자</p></div><Button type="submit" disabled={loading === "consult"} size="lg" className="h-12 w-full bg-red-700 hover:bg-red-800">{loading === "consult" ? <Loader2 className="h-5 w-5 animate-spin" /> : <ShieldAlert className="h-5 w-5" />}사고 대처 안내 받기</Button></form></CardContent></Card>
          {consultResult && <ConsultResult result={consultResult} onCorrectType={(type) => consult(undefined, type)} loading={loading === "consult"} />}
        </TabsContent>

        <TabsContent value="preview" className="m-0 space-y-6">
          <Card className="border-2"><CardHeader><CardTitle className="text-xl">재해유형별 대처 미리보기</CardTitle><CardDescription>평상시에 특정 사고가 발생했을 때의 조치 순서를 확인합니다.</CardDescription></CardHeader><CardContent><form onSubmit={preview} className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end"><div className="space-y-2"><Label htmlFor="preview-industry">업종</Label><Select value={industry} onValueChange={setIndustry}><SelectTrigger id="preview-industry"><SelectValue /></SelectTrigger><SelectContent>{industries.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div><div className="space-y-2"><Label htmlFor="preview-type">재해유형</Label><Select value={previewType} onValueChange={setPreviewType}><SelectTrigger id="preview-type"><SelectValue placeholder="유형 선택" /></SelectTrigger><SelectContent>{commonAccidentTypes.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div><Button type="submit" disabled={loading === "preview"} className="h-10 bg-blue-600 hover:bg-blue-700">{loading === "preview" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}조회</Button></form></CardContent></Card>
          {previewResult && <PreviewResult result={previewResult} />}
        </TabsContent>
      </Tabs>

      {error && <div role="alert" className="mt-6 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">{error}</div>}
    </div>
  );
}

function ConsultResult({ result, onCorrectType, loading }: { result: AccidentConsultResponse; onCorrectType: (type: string) => void; loading: boolean }) {
  const severityTone = result.severity.level === "MINOR" ? "border-green-200 bg-green-50" : "border-red-200 bg-red-50";
  return <div className="space-y-6">
    <Card className={`border-2 ${severityTone}`}><CardHeader><div className="flex flex-wrap items-start justify-between gap-3"><div><CardTitle className="text-xl">{result.accidentType} 사고로 분석됐습니다</CardTitle><CardDescription>{result.severity.note}</CardDescription></div><Badge variant={result.severity.seriousAccidentLikely ? "destructive" : "default"}>{severityLabels[result.severity.level]}</Badge></div></CardHeader><CardContent className="space-y-4">{!result.accidentTypeCertain && <div className="rounded-md border border-yellow-300 bg-yellow-50 p-4"><p className="font-semibold text-yellow-950">사고 유형을 확인해 주세요</p><p className="mt-1 text-sm text-yellow-800">추정 유형이 확실하지 않습니다. 실제 유형을 선택하면 다시 분석합니다.</p><div className="mt-3 max-w-xs"><Select onValueChange={onCorrectType} disabled={loading}><SelectTrigger aria-label="사고 유형 수정"><SelectValue placeholder="다른 유형 선택" /></SelectTrigger><SelectContent>{result.selectableTypes.map((type) => <SelectItem key={type} value={type}>{type}</SelectItem>)}</SelectContent></Select></div></div>}<div><h3 className="font-semibold text-gray-950">중대재해 판단 기준</h3><ul className="mt-2 space-y-2">{result.severity.criteria.map((criterion) => <li key={criterion} className="flex gap-2 text-sm text-gray-700"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />{criterion}</li>)}</ul><p className="mt-2 text-xs text-gray-500">기준: {result.severity.criteriaBasis}</p></div></CardContent></Card>
    {result.mode === "RETRIEVAL_ONLY" && result.note && <div className="flex gap-2 rounded-md border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-900"><Info className="h-5 w-5 shrink-0" />{result.note}</div>}
    <ActionTimeline actions={result.immediateActions} />
    <div className="grid gap-6 lg:grid-cols-3"><GuidanceCard title="법적 의무" icon={<Gavel className="h-5 w-5" />} section={result.legalObligations} tone="red" /><GuidanceCard title="행정 처리" icon={<FileWarning className="h-5 w-5" />} section={result.administrativeSteps} tone="blue" /><GuidanceCard title="위반 시 위험" icon={<AlertTriangle className="h-5 w-5" />} section={result.penaltyRisk} tone="orange" /></div>
    <SimilarCases cases={result.similarCases} note={result.similarCaseNote} />
    <div className="flex gap-2 rounded-md border border-gray-300 bg-gray-50 px-4 py-3 text-sm text-gray-700"><Info className="h-5 w-5 shrink-0" />{result.disclaimer}</div>
  </div>;
}

function PreviewResult({ result }: { result: AccidentResponseGuide }) {
  return <div className="space-y-6"><ActionTimeline actions={result.actions} />{result.lawBasis.length > 0 && <Card><CardHeader><CardTitle className="text-lg">근거 법령</CardTitle></CardHeader><CardContent><ul className="space-y-3">{result.lawBasis.map((law) => <li key={`${law.lawName}-${law.articleNo}-${law.clauseNo}`} className="text-sm"><strong>{law.lawName} {law.articleNo}{law.clauseNo ? ` ${law.clauseNo}` : ""}</strong><p className="text-gray-600">{law.title} · 관련 점검항목 {law.referencedBy}개</p></li>)}</ul></CardContent></Card>}<SimilarCases cases={result.similarCases} note={result.similarCaseNote} /><div className="flex gap-2 rounded-md border border-gray-300 bg-gray-50 px-4 py-3 text-sm text-gray-700"><Info className="h-5 w-5 shrink-0" />{result.disclaimer}</div></div>;
}

function ActionTimeline({ actions }: { actions: ImmediateAction[] }) {
  const immediate = actions.filter((action) => action.immediate);
  const followUp = actions.filter((action) => !action.immediate);
  return <Card className="border-2"><CardHeader><CardTitle className="flex items-center gap-2 text-xl"><Clock3 className="h-5 w-5 text-red-600" />조치 순서</CardTitle></CardHeader><CardContent className="space-y-5">{[["즉시 조치", immediate], ["후속 처리", followUp]].map(([label, items]) => (items as ImmediateAction[]).length > 0 && <section key={label as string}><h3 className="mb-3 font-semibold text-gray-950">{label as string}</h3><ol className="space-y-4">{(items as ImmediateAction[]).map((action) => <li key={`${action.step}-${action.title}`} className="flex gap-3"><span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-red-700 text-sm font-bold text-white">{action.step}</span><div><p className="font-semibold text-gray-950">{action.title}</p><p className="mt-1 text-sm leading-6 text-gray-700">{action.description}</p>{action.legalBasis && <p className="mt-1 text-xs font-medium text-blue-800">{action.legalBasis}</p>}</div></li>)}</ol></section>)}</CardContent></Card>;
}

function GuidanceCard({ title, icon, section, tone }: { title: string; icon: React.ReactNode; section: GuidanceSection; tone: "red" | "blue" | "orange" }) {
  const toneClass = tone === "red" ? "text-red-700" : tone === "blue" ? "text-blue-700" : "text-orange-700";
  return <Card className="border-2"><CardHeader><CardTitle className={`flex items-center gap-2 text-lg ${toneClass}`}>{icon}{title}</CardTitle></CardHeader><CardContent className="space-y-4">{section.guidance && <p className="text-sm leading-6 text-gray-700">{section.guidance}</p>}<ul className="divide-y divide-gray-200">{section.items.map((item) => <li key={`${item.title}-${item.legalBasis}`} className="py-3 first:pt-0"><p className="font-semibold text-gray-950">{item.title}</p><p className="mt-1 text-sm leading-6 text-gray-600">{item.detail}</p>{item.deadline && <p className="mt-2 text-xs font-semibold text-red-700">기한: {item.deadline}</p>}{item.legalBasis && <p className="mt-1 text-xs text-blue-800">{item.legalBasis}</p>}</li>)}</ul></CardContent></Card>;
}

function SimilarCases({ cases, note }: { cases: AccidentConsultResponse["similarCases"]; note: string | null }) {
  return <Card><CardHeader><CardTitle className="text-lg">관련 중대재해 사례</CardTitle></CardHeader><CardContent>{cases.length ? <div className="space-y-5">{cases.map((item) => <section key={item.sifId}><p className="font-semibold text-gray-950">{item.accidentKind} 사례 #{item.sifId}</p><p className="mt-2 text-sm leading-6 text-gray-700">{item.summary}</p>{item.countermeasures.length > 0 && <ul className="mt-3 space-y-1">{item.countermeasures.map((measure) => <li key={measure} className="text-sm text-gray-600">• {measure}</li>)}</ul>}</section>)}</div> : <div className="flex gap-2 text-sm text-gray-600"><Info className="h-5 w-5 shrink-0" />{note || "표시할 유사 사례가 없습니다."}</div>}</CardContent></Card>;
}

const severityLabels = { FATAL: "사망 위험 표현", SEVERE: "중한 부상 가능", MINOR: "경미한 부상", UNKNOWN: "중대 가능성 확인 필요" } as const;
