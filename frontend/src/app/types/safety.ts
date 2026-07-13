export interface BusinessData {
  industryMajor: string;
  industryMid: string;
  region: string;
  workerCount: number;
}

export interface RiskData {
  overallRisk: number; // 0-100
  riskLevel: "safe" | "caution" | "danger";
  topAccidents: Array<{
    type: string;
    percentage: number;
  }>;
  comparisonText: string;
}

export interface AccidentCase {
  id: string;
  date: string;
  location: string;
  cause: string;
  result: string;
  fullDescription: string;
  aiAdvice: string;
}

export interface ChecklistItem {
  id: string;
  title: string;
  description: string;
  actionLink?: string;
  checked: boolean;
}
