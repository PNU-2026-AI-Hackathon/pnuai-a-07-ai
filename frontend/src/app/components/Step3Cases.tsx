import { useState } from "react";
import { useNavigate } from "react-router";
import { useSafety } from "../contexts/SafetyContext";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "./ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "./ui/dialog";
import { Badge } from "./ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./ui/tabs";
import {
  AlertCircle,
  ArrowRight,
  Bot,
  Building2,
  Calendar,
  ExternalLink,
  Gavel,
  Info,
  Landmark,
  MapPin,
  Scale,
  ShieldCheck,
} from "lucide-react";
import { motion } from "motion/react";
import { useEffect } from "react";

const aiAdviceDetails = [
  {
    id: "law",
    title: "법령",
    subtitle: "사고와 직접 관련된 안전 의무",
    sortLabel: "관련도순",
    icon: Scale,
    tone: "blue",
    items: [
      {
        title: "산업안전보건기준에 관한 규칙 제13조",
        meta: "안전난간의 구조 및 설치요건",
        detail: "작업발판 끝이나 개구부처럼 추락할 위험이 있는 장소에는 상부·중간 난간대와 발끝막이판을 갖춘 안전난간을 설치해야 합니다.",
        badge: "관련 자료 17건",
        sortValue: 17,
      },
      {
        title: "산업안전보건기준에 관한 규칙 제42조",
        meta: "추락의 방지",
        detail: "높이 2m 이상에서 작업할 때 작업발판을 설치하고, 설치가 곤란하면 안전방망 또는 안전대 부착설비를 마련해야 합니다.",
        badge: "관련 자료 8건",
        sortValue: 8,
      },
    ],
  },
  {
    id: "admin",
    title: "행정",
    subtitle: "사고 직후 사업주가 해야 할 조치",
    sortLabel: "우선순위순",
    icon: Building2,
    tone: "orange",
    items: [
      {
        title: "즉시 · 작업 중지 및 현장 보존",
        meta: "우선순위 1",
        detail: "동일 작업을 즉시 중지하고 근로자를 안전한 장소로 대피시킨 뒤, 원인조사가 끝날 때까지 사고 현장을 임의로 변경하지 마세요.",
        badge: "긴급",
        sortValue: -1,
      },
      {
        title: "즉시 · 중대재해 발생 보고",
        meta: "관할 지방고용노동관서",
        detail: "재해 개요, 피해 상황, 조치 내용을 전화·팩스 등으로 지체 없이 보고해야 합니다.",
        badge: "긴급",
        sortValue: -1,
      },
      {
        title: "1개월 이내 · 산업재해조사표 제출",
        meta: "관할 지방고용노동관서",
        detail: "사망 또는 3일 이상 휴업이 필요한 재해는 산업재해조사표를 작성해 제출하세요.",
        badge: "후속",
        sortValue: -2,
      },
    ],
  },
  {
    id: "policy",
    title: "정책",
    subtitle: "안전시설 개선에 활용할 지원",
    sortLabel: "사업주 지원사업",
    icon: Landmark,
    tone: "emerald",
    items: [
      {
        title: "산재예방시설 융자",
        meta: "시설자금 · 고용노동부",
        detail: "안전난간, 추락방지망 등 재해예방 시설 개선 비용을 장기·저리 융자로 지원합니다.",
        badge: "시설자금",
        sortValue: 0,
        link: "https://www.gov.kr/portal/rcvfvrSvc/dtlEx/149200000006",
      },
      {
        title: "안전보건관리체계 구축 컨설팅",
        meta: "기술지원 · 안전보건공단",
        detail: "소규모 사업장이 위험성평가와 안전보건관리 절차를 갖추도록 전문가 방문 컨설팅을 지원합니다.",
        badge: "기술지원",
        sortValue: 0,
      },
    ],
  },
  {
    id: "precedent",
    title: "판례",
    subtitle: "유사 사고에서 법원이 본 책임",
    sortLabel: "최신 선고순",
    icon: Gavel,
    tone: "violet",
    items: [
      {
        title: "대법원 2025도428",
        meta: "2025.08.14 선고",
        detail: "사업주는 작업 장소와 방식에 따른 구체적인 추락 위험을 확인하고, 실제로 작동하는 안전조치를 마련할 의무가 있다고 판단했습니다.",
        badge: "대법원",
        sortValue: new Date("2025-08-14").getTime(),
      },
      {
        title: "대법원 2025도5060",
        meta: "2026.01.29 선고",
        detail: "안전보건 조치가 서류에만 존재하는 것으로는 부족하며, 현장에서 지속적으로 이행·점검되었는지가 책임 판단의 핵심이라고 보았습니다.",
        badge: "대법원",
        sortValue: new Date("2026-01-29").getTime(),
      },
    ],
  },
] as const;

// 실제 연동에서는 JSO의 각 items 배열을 같은 화면 모델로 변환합니다.
// 빈 배열은 이 단계에서 제거되어 탭 자체가 렌더링되지 않습니다.
const visibleAdviceSections = aiAdviceDetails
  .filter((section) => section.items.length > 0)
  .map((section) => ({
    ...section,
    items: [...section.items].sort((a, b) => b.sortValue - a.sortValue),
  }));

const adviceTone = {
  blue: {
    icon: "bg-blue-100 text-blue-700",
    badge: "bg-blue-50 text-blue-700 border-blue-200",
  },
  orange: {
    icon: "bg-orange-100 text-orange-700",
    badge: "bg-orange-50 text-orange-700 border-orange-200",
  },
  emerald: {
    icon: "bg-emerald-100 text-emerald-700",
    badge: "bg-emerald-50 text-emerald-700 border-emerald-200",
  },
  violet: {
    icon: "bg-violet-100 text-violet-700",
    badge: "bg-violet-50 text-violet-700 border-violet-200",
  },
} as const;

export default function Step3Cases() {
  const navigate = useNavigate();
  const { businessData, accidentCases } = useSafety();
  const [selectedCase, setSelectedCase] = useState<string | null>(null);
  const [activeAdviceTab, setActiveAdviceTab] = useState(
    visibleAdviceSections[0]?.id ?? "",
  );
  
  useEffect(() => {
    if (!businessData || accidentCases.length === 0) {
      navigate("/");
    }
  }, [businessData, accidentCases, navigate]);

  useEffect(() => {
    if (selectedCase) {
      setActiveAdviceTab(visibleAdviceSections[0]?.id ?? "");
    }
  }, [selectedCase]);
  
  if (!businessData || accidentCases.length === 0) {
    return null;
  }
  
  const selectedCaseData = accidentCases.find(c => c.id === selectedCase);
  
  return (
    <div className="container max-w-6xl mx-auto px-4 py-8">
      <div className="text-center mb-8">
        <div className="inline-flex items-center gap-2 bg-orange-100 text-orange-700 px-4 py-2 rounded-full mb-4">
          <AlertCircle className="w-5 h-5" />
          <span className="text-sm font-medium">STEP 3 / 4</span>
        </div>
        <h1 className="text-3xl md:text-4xl font-bold mb-2 text-gray-900">
          🟠 맞춤형 재해 사례
        </h1>
        <p className="text-gray-600">
          {businessData.industryMajor} · {businessData.industryMid} · {businessData.region}와 유사한 실제 사고 사례
        </p>
      </div>
      
      <div className="mb-8 bg-blue-50 border-2 border-blue-200 rounded-lg p-4">
        <p className="text-sm text-blue-800">
          <Info className="w-4 h-4 inline mr-2" />
          아래 사례들은 사장님의 사업장과 유사한 조건에서 발생한 실제 산업재해입니다.
          <strong className="ml-1">남의 일이 아닙니다.</strong>
        </p>
      </div>
      
      {/* Accident Case Cards */}
      <div className="grid md:grid-cols-2 gap-6 mb-8">
        {accidentCases.map((accidentCase, index) => (
          <motion.div
            key={accidentCase.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: index * 0.1 }}
          >
            <Card className="border-2 shadow-lg hover:shadow-xl transition-shadow cursor-pointer h-full"
                  onClick={() => setSelectedCase(accidentCase.id)}>
              <CardHeader>
                <div className="flex items-start justify-between mb-2">
                  <Badge variant="destructive" className="text-xs">
                    실제 사례
                  </Badge>
                  <Badge variant="outline" className="text-xs">
                    사례 #{accidentCase.id}
                  </Badge>
                </div>
                <CardTitle className="text-lg line-clamp-2">
                  {accidentCase.cause}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-2 text-sm">
                  <div className="flex items-start gap-2 text-gray-600">
                    <Calendar className="w-4 h-4 mt-0.5 flex-shrink-0" />
                    <span>{accidentCase.date}</span>
                  </div>
                  <div className="flex items-start gap-2 text-gray-600">
                    <MapPin className="w-4 h-4 mt-0.5 flex-shrink-0" />
                    <span>{accidentCase.location}</span>
                  </div>
                  <div className="flex items-start gap-2 text-red-600 font-semibold">
                    <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                    <span>{accidentCase.result}</span>
                  </div>
                </div>
                
                {/* AI Advice */}
                <div className="bg-gradient-to-r from-blue-50 to-cyan-50 border-l-4 border-blue-500 rounded-r-lg p-3 mt-4">
                  <div className="flex items-start gap-2">
                    <Bot className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <p className="text-xs font-semibold text-blue-800 mb-1">AI 한 줄 평</p>
                      <p className="text-sm text-gray-700">{accidentCase.aiAdvice}</p>
                    </div>
                  </div>
                </div>
                
                <Button 
                  variant="outline" 
                  size="sm" 
                  className="w-full mt-2"
                  onClick={(e) => {
                    e.stopPropagation();
                    setSelectedCase(accidentCase.id);
                  }}
                >
                  상세보기
                </Button>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>
      
      {/* Action Buttons */}
      <div className="flex flex-col sm:flex-row gap-4 justify-center">
        <Button
          variant="outline"
          size="lg"
          onClick={() => navigate("/report")}
          className="h-12"
        >
          이전 단계
        </Button>
        <Button
          size="lg"
          onClick={() => navigate("/checklist")}
          className="h-12 bg-gradient-to-r from-red-600 to-orange-600 hover:from-red-700 hover:to-orange-700"
        >
          안전 체크리스트 확인하기
          <ArrowRight className="w-5 h-5 ml-2" />
        </Button>
      </div>
      
      {/* Detail Modal */}
      <Dialog open={!!selectedCase} onOpenChange={() => setSelectedCase(null)}>
        <DialogContent className="max-h-[90vh] max-w-4xl overflow-y-auto p-0 sm:max-w-4xl">
          {selectedCaseData && (
            <>
              <DialogHeader className="border-b border-gray-100 px-5 pb-5 pt-6 text-left sm:px-7">
                <DialogTitle className="pr-8 text-xl sm:text-2xl">
                  {selectedCaseData.cause}
                </DialogTitle>
                <DialogDescription asChild>
                  <div className="space-y-4 pt-3">
                    <div className="grid gap-3 text-sm sm:grid-cols-2">
                      <div className="flex items-center gap-2 text-gray-600">
                        <Calendar className="h-4 w-4 text-gray-400" />
                        <span><strong className="text-gray-800">발생일</strong> {selectedCaseData.date}</span>
                      </div>
                      <div className="flex items-center gap-2 text-gray-600">
                        <MapPin className="h-4 w-4 text-gray-400" />
                        <span><strong className="text-gray-800">발생장소</strong> {selectedCaseData.location}</span>
                      </div>
                    </div>
                    <div className="flex gap-3 rounded-lg border border-red-200 bg-red-50 p-4">
                      <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
                      <div>
                        <span className="font-semibold text-red-900">피해 결과</span>
                        <p className="mt-0.5 text-red-700">{selectedCaseData.result}</p>
                      </div>
                    </div>
                  </div>
                </DialogDescription>
              </DialogHeader>
              
              <div className="space-y-6 px-5 py-6 sm:px-7">
                <div>
                  <h4 className="mb-2 text-lg font-semibold text-gray-900">사고 경위</h4>
                  <p className="leading-relaxed text-gray-700">
                    {selectedCaseData.fullDescription}
                  </p>
                </div>
                
                <section aria-labelledby="ai-advice-title" className="overflow-hidden rounded-xl border border-blue-200">
                  <div className="border-b border-blue-100 bg-blue-50 px-4 py-4 sm:px-5">
                    <div className="flex items-start gap-3">
                      <div className="rounded-lg bg-blue-600 p-2 text-white">
                        <Bot className="h-5 w-5" />
                      </div>
                      <div>
                        <h4 id="ai-advice-title" className="font-semibold text-blue-950">AI 사고 대응 조언</h4>
                        <p className="mt-1 text-sm leading-relaxed text-blue-800">
                          {selectedCaseData.aiAdvice} 아래 법령·행정·정책·판례를 함께 확인해 후속 조치를 준비하세요.
                        </p>
                      </div>
                    </div>
                  </div>

                  {visibleAdviceSections.length > 0 ? (
                    <Tabs
                      value={activeAdviceTab}
                      onValueChange={setActiveAdviceTab}
                      className="gap-0 bg-white"
                    >
                      <TabsList className="flex h-auto w-full flex-wrap rounded-none border-b border-gray-200 bg-white p-0">
                        {visibleAdviceSections.map((section) => {
                          const Icon = section.icon;
                          return (
                            <TabsTrigger
                              key={section.id}
                              value={section.id}
                              className="h-12 basis-1/2 rounded-none border-0 border-b-2 border-transparent px-3 text-gray-500 shadow-none data-[state=active]:border-blue-600 data-[state=active]:bg-blue-50/60 data-[state=active]:font-semibold data-[state=active]:text-blue-700 sm:h-14 sm:basis-0"
                            >
                              <Icon className="h-4 w-4" />
                              <span>{section.title}</span>
                              <span className="text-xs">{section.items.length}</span>
                            </TabsTrigger>
                          );
                        })}
                      </TabsList>

                      {visibleAdviceSections.map((section) => {
                        const Icon = section.icon;
                        const tone = adviceTone[section.tone];
                        return (
                          <TabsContent key={section.id} value={section.id} className="m-0">
                            <div className="flex flex-col gap-3 border-b border-gray-100 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
                              <div className="flex items-center gap-3">
                                <div className={`rounded-lg p-2 ${tone.icon}`}>
                                  <Icon className="h-5 w-5" />
                                </div>
                                <div>
                                  <h5 className="font-semibold text-gray-950">관련 {section.title}</h5>
                                  <p className="text-xs text-gray-500">{section.subtitle}</p>
                                </div>
                              </div>
                              <span className="self-start rounded-full bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-600 sm:self-auto">
                                {section.sortLabel}
                              </span>
                            </div>

                            <ul className="divide-y divide-gray-200">
                              {section.items.map((item) => (
                                <li key={item.title} className="px-4 py-4 sm:px-5">
                                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between sm:gap-5">
                                    <div className="min-w-0 flex-1">
                                      <div className="flex flex-wrap items-center gap-2">
                                        <p className="font-semibold leading-snug text-gray-950">{item.title}</p>
                                        <span className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${tone.badge}`}>
                                          {item.badge}
                                        </span>
                                      </div>
                                      <p className="mt-1 text-sm font-medium text-gray-600">{item.meta}</p>
                                      <p className="mt-2 text-sm leading-relaxed text-gray-600">{item.detail}</p>
                                      {"link" in item && item.link && (
                                        <a
                                          href={item.link}
                                          target="_blank"
                                          rel="noreferrer"
                                          className="mt-3 inline-flex min-h-8 items-center gap-1 text-sm font-semibold text-blue-700 hover:underline"
                                        >
                                          지원사업 상세보기
                                          <ExternalLink className="h-3.5 w-3.5" />
                                        </a>
                                      )}
                                    </div>
                                  </div>
                                </li>
                              ))}
                            </ul>
                          </TabsContent>
                        );
                      })}
                    </Tabs>
                  ) : (
                    <div className="px-5 py-8 text-center text-sm text-gray-500">
                      이 사고와 연결된 상세 조언이 없습니다.
                    </div>
                  )}

                  <div className="flex gap-2 border-t border-gray-200 bg-gray-50 px-4 py-3 text-xs leading-relaxed text-gray-500 sm:px-5">
                    <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-gray-500" />
                    <div>
                      AI 조언은 제공된 사고 관련 데이터를 바탕으로 구성된 참고 정보입니다. 실제 적용 전 관할 기관과 전문가에게 확인하세요.
                    </div>
                  </div>
                </section>
              </div>
              
              <div className="flex justify-end border-t border-gray-100 px-5 py-4 sm:px-7">
                <Button onClick={() => setSelectedCase(null)} className="min-w-24">
                  닫기
                </Button>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
