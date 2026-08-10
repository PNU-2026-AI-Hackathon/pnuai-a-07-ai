import type { ChecklistItem, PreventionGuideResponse, RiskAssessment, SimilarCaseResponse, Workplace } from "../types/safety";

// 개발 서버에서 화면 흐름을 확인하기 위한 예시 진단 데이터입니다.
export const previewWorkplace: Workplace = {
  id: 0,
  name: "동헌금속",
  industry: "제조업",
  subIndustry: "금속가공",
  sizeClass: "20~29인",
  region: "부산",
  employeeCount: 20,
  address: "부산 사상구",
  createdAt: "2026-08-10T00:00:00",
};

export const previewPreventionGuide: PreventionGuideResponse = {
  predictions: [
    { rank: 1, accidentType: "끼임", ratio: 0.42, deathRatio: 0.18, checklist: [{ itemCode: "MCH-01", workType: "기계 작업", question: "작업 전 방호장치를 점검했나요?", riskWeight: 5, isCritical: true, lawBasis: [] }, { itemCode: "MCH-02", workType: "기계 작업", question: "정비 전 전원을 차단했나요?", riskWeight: 5, isCritical: true, lawBasis: [] }] },
    { rank: 2, accidentType: "넘어짐", ratio: 0.33, deathRatio: 0.07, checklist: [{ itemCode: "FAL-01", workType: "이동 작업", question: "통로의 미끄럼 위험을 제거했나요?", riskWeight: 3, isCritical: false, lawBasis: [] }] },
    { rank: 3, accidentType: "부딪힘", ratio: 0.25, deathRatio: 0.05, checklist: [{ itemCode: "COL-01", workType: "운반 작업", question: "작업 구역을 분리했나요?", riskWeight: 3, isCritical: false, lawBasis: [] }] },
  ],
};

export const previewCases: SimilarCaseResponse = {
  industry: "제조업",
  subIndustry: "금속가공",
  topKeywords: ["프레스", "방호장치", "끼임"],
  totalCount: 3,
  note: "개발용 예시 사례입니다.",
  cases: [
    { sifId: 101, summary: "프레스 금형을 정비하던 작업자가 전원을 차단하지 않아 손가락이 끼이는 사고가 발생했습니다.", countermeasures: ["정비 전 전원 차단과 잠금 장치를 확인합니다.", "방호장치 해제 작업은 관리자가 확인합니다."], score: 0.91 },
    { sifId: 204, summary: "가공품 운반 중 통로의 절삭유에 미끄러져 작업자가 넘어지는 사고가 발생했습니다.", countermeasures: ["통로의 기름과 이물질을 즉시 제거합니다.", "미끄럼 방지 안전화를 착용합니다."], score: 0.83 },
    { sifId: 317, summary: "지게차 이동 구역과 보행 통로가 분리되지 않아 작업자가 적재물에 부딪혔습니다.", countermeasures: ["보행 통로와 운반 구역을 구분합니다.", "작업 전 이동 경로를 공유합니다."], score: 0.74 },
  ],
};

export const previewChecklistItems: ChecklistItem[] = [
  { itemCode: "MCH-01", category: "끼임", workType: "기계 작업", question: "프레스와 절단기의 방호장치가 정상 작동하나요?", description: "방호장치를 제거하거나 무력화한 상태로 작업하면 끼임 사고 위험이 높습니다.", riskWeight: 5, isCritical: true },
  { itemCode: "MCH-02", category: "끼임", workType: "정비 작업", question: "정비·청소 전에 전원을 차단하고 잠금 조치를 하나요?", description: "기계가 갑자기 작동하지 않도록 전원을 격리해야 합니다.", riskWeight: 5, isCritical: true },
  { itemCode: "FAL-01", category: "넘어짐", workType: "이동 작업", question: "통로의 기름, 물기, 적재물을 바로 제거하나요?", description: "미끄럼과 걸림 위험이 없는 통로를 유지합니다.", riskWeight: 3, isCritical: false },
  { itemCode: "COL-01", category: "부딪힘", workType: "운반 작업", question: "보행 통로와 지게차 이동 구역을 분리했나요?", description: "운반장비와 작업자의 이동 구역이 겹치지 않게 표시합니다.", riskWeight: 3, isCritical: false },
];

export const previewRiskAssessment: RiskAssessment = {
  assessmentId: 0, workplaceId: 0, submissionId: 0, method: "HYBRID", riskScore: 62.5, riskGrade: "HIGH", topAccidentType: "끼임", baseComponent: 38, checklistComponent: 24.5, matchLevel: "EXACT", modelVersion: "preview", assessedAt: "2026-08-10T00:00:00",
  topRisks: [{ type: "끼임", probability: 0.42, shap_value: null }, { type: "넘어짐", probability: 0.33, shap_value: null }, { type: "부딪힘", probability: 0.25, shap_value: null }],
  severityPrediction: [{ label: "중상", probability: 0.54 }, { label: "경상", probability: 0.31 }, { label: "사망", probability: 0.15 }],
};
