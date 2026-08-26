import { FormEvent, useState, type ReactNode } from "react";
import { AlertTriangle, BookOpen, CheckCircle2, Clock3, ExternalLink, FileWarning, Gavel, Info, Loader2, Search, ShieldAlert, Stethoscope } from "lucide-react";
import { useSafety } from "../contexts/SafetyContext";
import type { AccidentConsultResponse, AccidentResponseGuide, Duty, ImmediateAction } from "../types/safety";
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
        <p className="mt-3 text-gray-600">즉시 조치, 법적 의무, 행정 처리와 위반 위험을 근거 자료와 함께 안내합니다.</p>
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
    <SmartAdviceTabs result={result} />
    {result.similarCases.length > 0 && <SimilarCases cases={result.similarCases} note={result.similarCaseNote} />}
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

type AdviceTabId = "legal" | "administration" | "penalty";

interface AdviceTab {
  id: AdviceTabId;
  label: string;
  count: number;
  description: string;
  icon: ReactNode;
  tone: "blue" | "indigo" | "orange";
}

function SmartAdviceTabs({ result }: { result: AccidentConsultResponse }) {
  const lawArticles = [...(result.citedArticles ?? [])].sort((left, right) =>
    (right.score ?? right.matchedTerms ?? 0) - (left.score ?? left.matchedTerms ?? 0));

  const tabs: AdviceTab[] = [
    result.legalObligations.items.length > 0 ? { id: "legal", label: "법적 의무", count: result.legalObligations.items.length, description: "사고 직후 사업주가 지켜야 할 의무와 근거 조문", icon: <Gavel className="h-5 w-5" />, tone: "blue" } : null,
    result.administrativeSteps.items.length > 0 ? { id: "administration", label: "행정 처리", count: result.administrativeSteps.items.length, description: "신고·제출·보험 등 사고 후 처리 절차", icon: <FileWarning className="h-5 w-5" />, tone: "indigo" } : null,
    result.penaltyRisk.items.length > 0 ? { id: "penalty", label: "위반 시 위험", count: result.penaltyRisk.items.length, description: "의무를 이행하지 않았을 때 발생할 수 있는 처벌 위험", icon: <AlertTriangle className="h-5 w-5" />, tone: "orange" } : null,
  ].filter((tab): tab is AdviceTab => tab !== null);

  if (tabs.length === 0) return null;

  return (
    <section aria-labelledby="smart-advice-title" className="space-y-4">
      <div>
        <div className="flex items-center gap-2">
          <BookOpen className="h-5 w-5 text-blue-700" />
          <h2 id="smart-advice-title" className="text-xl font-bold text-gray-950">사고 대응 상세 안내</h2>
        </div>
        <p className="mt-1 text-sm text-gray-600">핵심 대응 내용을 세 영역으로 나눴습니다. 필요한 탭을 선택해 확인하세요.</p>
      </div>

      <Tabs defaultValue={tabs[0].id} className="w-full">
        <TabsList
          className="grid h-auto w-full gap-1 rounded-xl bg-gray-100 p-1"
          style={{ gridTemplateColumns: `repeat(${tabs.length}, minmax(0, 1fr))` }}
          aria-label="사고 대응 상세 안내 분류"
        >
          {tabs.map((tab) => (
            <TabsTrigger key={tab.id} value={tab.id} className={`min-h-12 gap-2 rounded-lg px-2 text-sm data-[state=active]:shadow-sm ${tab.tone === "blue" ? "data-[state=active]:bg-blue-50" : tab.tone === "indigo" ? "data-[state=active]:bg-indigo-50" : "data-[state=active]:bg-orange-50"}`}>
              <span className="hidden sm:inline-flex">{tab.icon}</span>
              <span>{tab.label}</span>
              <span className="rounded-full bg-gray-200 px-2 py-0.5 text-xs font-semibold text-gray-700">{tab.count}</span>
            </TabsTrigger>
          ))}
        </TabsList>

        {tabs.map((tab) => (
          <TabsContent key={tab.id} value={tab.id} className="mt-4 focus-visible:outline-none">
            <AdvicePanel tab={tab}>
              {tab.id === "legal" && <LegalAdvice result={result} articles={lawArticles} />}
              {tab.id === "administration" && (
                <div className="p-5 md:p-6">
                  {result.administrativeSteps.guidance && <p className="mb-4 text-sm leading-6 text-gray-700">{result.administrativeSteps.guidance}</p>}
                  <DutyList items={result.administrativeSteps.items} showAdministrativeDetails tone="administration" />
                </div>
              )}
              {tab.id === "penalty" && (
                <div className="p-5 md:p-6">
                  {result.penaltyRisk.guidance && <p className="mb-4 text-sm leading-6 text-gray-700">{result.penaltyRisk.guidance}</p>}
                  <DutyList items={result.penaltyRisk.items} tone="danger" />
                </div>
              )}
            </AdvicePanel>
          </TabsContent>
        ))}
      </Tabs>
    </section>
  );
}

function AdvicePanel({ tab, children }: { tab: AdviceTab; children: ReactNode }) {
  const toneStyles = {
    blue: { border: "border-blue-200", header: "border-blue-200 bg-blue-100/70", body: "bg-blue-50/60", icon: "text-blue-700" },
    indigo: { border: "border-indigo-200", header: "border-indigo-200 bg-indigo-100/70", body: "bg-indigo-50/60", icon: "text-indigo-700" },
    orange: { border: "border-orange-200", header: "border-orange-200 bg-orange-100/70", body: "bg-orange-50/60", icon: "text-orange-700" },
  }[tab.tone];
  return (
    <Card className={`overflow-hidden border-2 shadow-sm ${toneStyles.border}`}>
      <CardHeader className={`border-b ${toneStyles.header}`}>
        <div className="flex items-start gap-3">
          <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white shadow-sm ${toneStyles.icon}`}>{tab.icon}</span>
          <div>
            <CardTitle className="text-lg">{tab.label}</CardTitle>
            <CardDescription className="mt-1">{tab.description}</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className={`p-0 ${toneStyles.body}`}>{children}</CardContent>
    </Card>
  );
}

function LegalAdvice({ result, articles }: { result: AccidentConsultResponse; articles: AccidentConsultResponse["citedArticles"] }) {
  return (
    <div className="divide-y divide-gray-200">
      <section className="p-5 md:p-6">
        {result.legalObligations.guidance && <p className="text-sm leading-6 text-gray-700">{result.legalObligations.guidance}</p>}
        <DutyList items={result.legalObligations.items} />
      </section>
      {articles.length > 0 && <section className="p-5 md:p-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-bold text-gray-950">근거 조문</h3>
          <span className="text-xs font-medium text-blue-700">관련도순</span>
        </div>
        <ul className="mt-3 divide-y divide-gray-200">
          {articles.map((article) => (
            <li key={article.articleId} className="py-4 first:pt-0 last:pb-0">
              <p className="font-semibold text-gray-950">{article.lawName} {article.articleNo}{article.clauseNo ? ` ${article.clauseNo}` : ""}</p>
              {article.title && <p className="mt-1 text-sm font-medium text-blue-800">{article.title}</p>}
              <p className="mt-2 whitespace-pre-line text-sm leading-6 text-gray-600">{article.content}</p>
            </li>
          ))}
        </ul>
      </section>}
    </div>
  );
}

function DutyList({ items, showAdministrativeDetails = false, tone = "default" }: { items: Duty[]; showAdministrativeDetails?: boolean; tone?: "default" | "administration" | "danger" }) {
  if (items.length === 0) return null;
  const numberTone = tone === "danger" ? "bg-orange-100 text-orange-800" : tone === "administration" ? "bg-indigo-100 text-indigo-800" : "bg-blue-100 text-blue-800";
  return (
    <ol className="mt-3 divide-y divide-gray-200">
      {items.map((item, index) => (
        <li key={`${item.title}-${item.legalBasis}-${index}`} className="py-4 first:pt-0 last:pb-0">
          <div className="flex gap-3">
            <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold ${numberTone}`}>{index + 1}</span>
            <div className="min-w-0 flex-1">
              <p className="font-semibold text-gray-950">{item.title}</p>
              <p className="mt-1 text-sm leading-6 text-gray-700">{item.detail}</p>
              <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
                {item.deadline && <span className="font-semibold text-red-700">기한: {item.deadline}</span>}
                {showAdministrativeDetails && item.agency && <span className="text-gray-600">담당: {item.agency}</span>}
                {item.legalBasis && <span className="text-blue-800">{item.legalBasis}</span>}
              </div>
              {showAdministrativeDetails && item.penalty && <p className="mt-2 rounded-md bg-red-50 px-3 py-2 text-xs text-red-800">미이행 시: {item.penalty}</p>}
              {showAdministrativeDetails && item.formUrl && (
                <a href={item.formUrl} target="_blank" rel="noreferrer" className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-blue-700 hover:underline">
                  {item.formName || "관련 서식 확인"}<ExternalLink className="h-3.5 w-3.5" />
                </a>
              )}
            </div>
          </div>
        </li>
      ))}
    </ol>
  );
}

function SimilarCases({ cases, note }: { cases: AccidentConsultResponse["similarCases"]; note: string | null }) {
  return <Card><CardHeader><CardTitle className="text-lg">관련 중대재해 사례</CardTitle></CardHeader><CardContent>{cases.length ? <div className="space-y-5">{cases.map((item) => <section key={item.sifId}><p className="font-semibold text-gray-950">{item.accidentKind} 사례 #{item.sifId}</p><p className="mt-2 text-sm leading-6 text-gray-700">{item.summary}</p>{item.countermeasures.length > 0 && <ul className="mt-3 space-y-1">{item.countermeasures.map((measure) => <li key={measure} className="text-sm text-gray-600">• {measure}</li>)}</ul>}</section>)}</div> : <div className="flex gap-2 text-sm text-gray-600"><Info className="h-5 w-5 shrink-0" />{note || "표시할 유사 사례가 없습니다."}</div>}</CardContent></Card>;
}

const severityLabels = { FATAL: "사망 위험 표현", SEVERE: "중한 부상 가능", MINOR: "경미한 부상", UNKNOWN: "중대 가능성 확인 필요" } as const;
