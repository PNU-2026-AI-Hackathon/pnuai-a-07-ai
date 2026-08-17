export interface TokenResponse {
  accessToken: string;
  tokenType: string;
}

export interface Me {
  userId: number;
  email: string;
  name: string;
  phone: string | null;
  role: "OWNER" | "ADMIN";
  createdAt: string;
}

export interface WorkplaceRequest {
  name: string;
  industry: string;
  subIndustry?: string;
  sizeClass: string;
  region: string;
  employeeCount?: number;
  address?: string;
}

export interface Workplace extends WorkplaceRequest {
  id: number;
  subIndustry: string | null;
  employeeCount: number | null;
  address: string | null;
  createdAt: string;
}

export interface WorkTypeReference {
  industry: string;
  workType: string;
  itemCount: number;
}

export interface ReferenceData {
  workTypes: WorkTypeReference[];
}

export type Answer = "YES" | "NO" | "NA";

export interface ChecklistItem {
  itemCode: string;
  category: string;
  workType: string;
  question: string;
  description: string | null;
  riskWeight: number;
  isCritical: boolean;
}

export interface ChecklistResponse {
  itemCode: string;
  answer: Answer;
  note?: string;
}

export type RiskGrade = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface RiskAssessment {
  assessmentId: number;
  workplaceId: number;
  submissionId: number | null;
  method: "COLDSTART" | "HYBRID";
  riskScore: number | null;
  riskGrade: RiskGrade | null;
  topAccidentType: string | null;
  baseComponent: number | null;
  checklistComponent: number | null;
  matchLevel: "EXACT" | "INDUSTRY_SIZE" | "INDUSTRY" | "NONE" | null;
  modelVersion: string;
  assessedAt: string;
  topRisks: TopRisk[];
  severityPrediction: SeverityPrediction[];
}

export interface TopRisk {
  type: string;
  probability: number;
  shap_value: number | null;
}

export interface SeverityPrediction {
  label: string;
  probability: number;
}

export interface ChecklistSubmitResponse {
  submissionId: number;
  totalItems: number;
  answeredItems: number;
  riskAssessment: RiskAssessment;
}

export interface PreventionChecklistItem {
  itemCode: string;
  workType: string;
  question: string;
  riskWeight: number;
  isCritical: boolean;
  lawBasis: string[];
}

export interface AccidentPrediction {
  rank: number;
  accidentType: string;
  ratio: number;
  deathRatio: number;
  checklist: PreventionChecklistItem[];
}

export interface PreventionGuideResponse {
  predictions: AccidentPrediction[];
}

export interface ReportCreateResponse {
  reportId: number;
  status: "PENDING" | "GENERATING" | "DONE" | "FAILED";
  fileSize: number | null;
  generatedAt: string | null;
}

export interface LawArticle {
  articleId: number;
  lawName: string;
  articleNo: string;
  clauseNo: string | null;
  title: string;
  content: string;
  source: "KEYWORD" | "SEMANTIC" | "STATUTE";
  score: number | null;
  matchedTerms: number | null;
}

export interface LawSearchResponse {
  query: string;
  mode: "HYBRID" | "KEYWORD";
  searchTerms: string[];
  totalCount: number;
  results: LawArticle[];
}

export interface SimilarCase {
  sifId: number;
  summary: string;
  countermeasures: string[];
  score: number | null;
}

export interface SimilarCaseResponse {
  industry: string;
  subIndustry: string | null;
  topKeywords: string[];
  totalCount: number;
  cases: SimilarCase[];
  note: string | null;
}

export interface ChatSession {
  sessionId: string;
  workplaceId: number | null;
  title: string | null;
  createdAt: string;
}

export interface AskResponse {
  sessionId: string;
  question: string;
  mode: "GENERATED" | "RETRIEVAL_ONLY";
  answer: string | null;
  citedArticles: LawArticle[];
  note: string | null;
  modelName: string | null;
}

export interface ChatMessage {
  messageId: number;
  role: "USER" | "ASSISTANT" | "SYSTEM";
  content: string;
  citedArticles: number[];
  modelName: string | null;
  createdAt: string;
}

export interface ImmediateAction {
  step: number;
  title: string;
  description: string;
  legalBasis: string | null;
  immediate: boolean;
}

export interface AccidentLawBasis {
  lawName: string;
  articleNo: string;
  clauseNo: string | null;
  title: string;
  referencedBy: number;
}

export interface AccidentSimilarCase {
  sifId: number;
  accidentKind: string;
  summary: string;
  highRiskSituation: string | null;
  causalFactor: string | null;
  countermeasures: string[];
}

export interface AccidentResponseGuide {
  accidentType: string;
  industry: string;
  disclaimer: string;
  actions: ImmediateAction[];
  lawBasis: AccidentLawBasis[];
  similarCases: AccidentSimilarCase[];
  similarCaseNote: string | null;
}

export interface AccidentConsultRequest {
  situation: string;
  industry?: string;
  accidentType?: string;
}

export interface Duty {
  title: string;
  detail: string;
  deadline: string | null;
  legalBasis: string | null;
  agency?: string | null;
  formName?: string | null;
  formUrl?: string | null;
  penalty?: string | null;
}

export interface AccidentPrecedent {
  caseName: string;
  court: string;
  reference: string | null;
  relevance: string;
  summary: string;
  url: string;
}

export interface AccidentSupportProgram {
  title: string;
  agency: string;
  relevance: string;
  summary: string;
  deadline: string | null;
  url: string;
}

export interface GuidanceSection {
  guidance: string | null;
  items: Duty[];
}

export interface AccidentSeverity {
  level: "FATAL" | "SEVERE" | "MINOR" | "UNKNOWN";
  seriousAccidentLikely: boolean;
  note: string;
  criteria: string[];
  criteriaBasis: string;
}

export interface AccidentConsultResponse {
  situation: string;
  accidentType: string;
  accidentTypeCertain: boolean;
  selectableTypes: string[];
  severity: AccidentSeverity;
  mode: "GENERATED" | "RETRIEVAL_ONLY";
  note: string | null;
  model: string | null;
  immediateActions: ImmediateAction[];
  legalObligations: GuidanceSection;
  administrativeSteps: GuidanceSection;
  penaltyRisk: GuidanceSection;
  relatedPrecedents: AccidentPrecedent[];
  supportPrograms: AccidentSupportProgram[];
  citedArticles: LawArticle[];
  similarCases: AccidentSimilarCase[];
  similarCaseNote: string | null;
  disclaimer: string;
}

export interface ApiErrorBody {
  error?: string;
  message?: string;
  fields?: Record<string, string>;
}
