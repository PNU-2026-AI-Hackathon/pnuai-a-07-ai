import type { RiskScopeCode } from "../types/safety";

export interface RiskScopeOption {
  code: RiskScopeCode;
  label: string;
  description: string;
}

export const RISK_SCOPE_OPTIONS: RiskScopeOption[] = [
  { code: "MACHINE_EQUIPMENT", label: "기계·설비 작업", description: "생산설비, 자동화설비, 정비·점검·청소 작업" },
  { code: "VEHICLE_HANDLING", label: "차량·운반 작업", description: "지게차, 건설기계, 양중기, 상하차·하역 작업" },
  { code: "WORK_AT_HEIGHT", label: "고소 작업", description: "사다리, 비계, 지붕, 작업발판 등 높은 곳의 작업" },
  { code: "ELECTRICAL", label: "전기 작업", description: "전기설비 설치·점검, 가설전기, 충전부 취급" },
  { code: "HOT_WORK", label: "화기 작업", description: "용접, 절단, 사상, 가열 또는 불꽃이 발생하는 작업" },
  { code: "CHEMICAL", label: "화학물질 취급", description: "위험물질, 유기용제, 도장·방수 재료 취급" },
  { code: "CONFINED_SPACE", label: "밀폐공간 작업", description: "피트, 맨홀, 탱크, 오·폐수 시설 내부 작업" },
  { code: "CONSTRUCTION", label: "건설·해체 작업", description: "굴착, 거푸집, 철골, 콘크리트, 철거·해체 작업" },
  { code: "STORAGE_LOGISTICS", label: "적재·보관 작업", description: "자재 적재, 창고 보관, 화물 상하차 작업" },
  { code: "GENERAL", label: "일반 작업 중심", description: "위 작업이 없거나 통행·이동·일반 점검 위주인 사업장" },
];

export const riskScopeLabel = (code: RiskScopeCode) =>
  RISK_SCOPE_OPTIONS.find((item) => item.code === code)?.label ?? code;
