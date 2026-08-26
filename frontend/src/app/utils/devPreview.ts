import type { ChecklistItem, PreventionGuideResponse, RiskAssessment, RiskScopeCode, SimilarCaseResponse, Workplace } from "../types/safety";

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
  machineType: "프레스, 절단기, 지게차",
  machineCount: 6,
  safetyDeviceStatus: "PARTIAL",
  storageLocation: "가공동 출입구 옆 적재구역",
  storageMethod: "철재 팔레트 2단 적재",
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
  recommendationBasis: "체크리스트 미비 위험(끼임, 넘어짐)과 현장 정보를 반영했습니다.",
  cases: [
    { sifId: 101, summary: "프레스 금형을 정비하던 작업자가 전원을 차단하지 않아 손가락이 끼이는 사고가 발생했습니다.", countermeasures: ["정비 전 전원 차단과 잠금 장치를 확인합니다.", "방호장치 해제 작업은 관리자가 확인합니다."], score: 0.91 },
    { sifId: 204, summary: "가공품 운반 중 통로의 절삭유에 미끄러져 작업자가 넘어지는 사고가 발생했습니다.", countermeasures: ["통로의 기름과 이물질을 즉시 제거합니다.", "미끄럼 방지 안전화를 착용합니다."], score: 0.83 },
    { sifId: 317, summary: "지게차 이동 구역과 보행 통로가 분리되지 않아 작업자가 적재물에 부딪혔습니다.", countermeasures: ["보행 통로와 운반 구역을 구분합니다.", "작업 전 이동 경로를 공유합니다."], score: 0.74 },
  ],
};

type PreviewTemplate = Omit<ChecklistItem, "itemCode"> & { scope: RiskScopeCode };

const previewChecklistTemplates: PreviewTemplate[] = [
  { scope: "MACHINE_EQUIPMENT", category: "끼임", workType: "기계·설비 작업", question: "기계의 회전부와 동력전달부에 방호덮개가 설치되어 있나요?", description: "방호장치를 제거하거나 무력화하면 끼임 위험이 커집니다.", riskWeight: 10, isCritical: true },
  { scope: "MACHINE_EQUIPMENT", category: "끼임", workType: "기계·설비 작업", question: "정비·청소 전에 전원을 차단하고 잠금 조치를 하나요?", description: "기계가 갑자기 작동하지 않도록 에너지를 격리해야 합니다.", riskWeight: 9, isCritical: true },
  { scope: "VEHICLE_HANDLING", category: "부딪힘", workType: "차량·운반 작업", question: "차량 이동구역과 보행 통로가 분리되어 있나요?", description: "작업자와 운반장비의 동선이 겹치지 않게 관리합니다.", riskWeight: 8, isCritical: true },
  { scope: "STORAGE_LOGISTICS", category: "깔림", workType: "적재·보관 작업", question: "적재물의 높이와 무너짐 방지 상태를 작업 전에 확인하나요?", description: "불안정한 적재물은 무너짐과 깔림 사고로 이어질 수 있습니다.", riskWeight: 8, isCritical: true },
  { scope: "WORK_AT_HEIGHT", category: "떨어짐", workType: "고소 작업", question: "높은 곳에서 작업할 때 안전난간이나 작업발판을 설치하나요?", description: "추락 위험 장소에는 우선 집단방호조치를 설치합니다.", riskWeight: 8, isCritical: true },
  { scope: "ELECTRICAL", category: "감전", workType: "전기 작업", question: "전기 작업 전 전원을 차단하고 검전하나요?", description: "충전 여부를 확인한 후 작업을 시작해야 합니다.", riskWeight: 7, isCritical: true },
  { scope: "HOT_WORK", category: "화재폭발", workType: "화기 작업", question: "화기 작업 전 가연물을 제거하고 소화기를 배치하나요?", description: "불티가 주변 가연물로 번지지 않게 관리합니다.", riskWeight: 7, isCritical: true },
  { scope: "CHEMICAL", category: "화학물질누출접촉", workType: "화학물질 취급", question: "취급 물질의 경고표지와 물질안전보건자료를 확인할 수 있나요?", description: "물질별 유해성과 대응방법을 작업 전에 확인합니다.", riskWeight: 6, isCritical: true },
  { scope: "CONFINED_SPACE", category: "질식", workType: "밀폐공간 작업", question: "밀폐공간 출입 전 산소와 유해가스 농도를 측정하나요?", description: "측정과 환기 없이 밀폐공간에 들어가면 안 됩니다.", riskWeight: 9, isCritical: true },
  { scope: "CONSTRUCTION", category: "무너짐", workType: "건설·해체 작업", question: "굴착·철거 작업 전에 붕괴 방지 계획을 확인하나요?", description: "작업 순서와 구조물 안정성을 먼저 검토합니다.", riskWeight: 8, isCritical: true },
  { scope: "GENERAL", category: "넘어짐", workType: "일반 작업", question: "통로의 물기와 장애물을 발견하면 즉시 제거하나요?", description: "모든 사업장에 공통으로 필요한 통행 안전 문항입니다.", riskWeight: 5, isCritical: true },
];

export function createPreviewChecklistItems(riskScopes: RiskScopeCode[]): ChecklistItem[] {
  const selectedTemplates = previewChecklistTemplates.filter((template) =>
    template.scope === "GENERAL" || riskScopes.includes(template.scope));
  return Array.from({ length: 30 }, (_, index) => {
    const { scope: _scope, ...template } = selectedTemplates[index % selectedTemplates.length];
    const zone = Math.floor(index / selectedTemplates.length) + 1;
    return {
      ...template,
      itemCode: `PVW-${String(index + 1).padStart(2, "0")}`,
      question: `${template.question} (점검구역 ${zone})`,
    };
  });
}

export const previewChecklistItems = createPreviewChecklistItems(["MACHINE_EQUIPMENT", "VEHICLE_HANDLING", "STORAGE_LOGISTICS"]);

export const previewRiskAssessment: RiskAssessment = {
  assessmentId: 0, workplaceId: 0, submissionId: 0, method: "HYBRID", riskScore: 62.5, riskGrade: "HIGH", topAccidentType: "끼임", baseComponent: 38, checklistComponent: 24.5, matchLevel: "EXACT", modelVersion: "preview", assessedAt: "2026-08-10T00:00:00",
  topRisks: [{ type: "끼임", probability: 0.42, shap_value: null, basis: "체크리스트 미비 2건과 동종 사업장 예측을 함께 반영" }, { type: "넘어짐", probability: 0.33, shap_value: null, basis: "체크리스트 미비 1건과 동종 사업장 예측을 함께 반영" }, { type: "부딪힘", probability: 0.25, shap_value: null, basis: "동종 사업장 통계·ML 예측" }],
  severityPrediction: [{ label: "중상", probability: 0.54 }, { label: "경상", probability: 0.31 }, { label: "사망", probability: 0.15 }],
};
