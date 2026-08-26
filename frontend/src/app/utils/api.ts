import { clearAuthToken, getAuthorizationHeader } from "./auth";
import type {
  AccidentConsultRequest,
  AccidentConsultResponse,
  AccidentResponseGuide,
  ApiErrorBody,
  AskResponse,
  ChatMessage,
  ChatSession,
  ChecklistItem,
  ChecklistResponse,
  ChecklistSubmitResponse,
  LawSearchResponse,
  Me,
  PreventionGuideResponse,
  ReferenceData,
  ReportCreateResponse,
  RiskScopeCode,
  RiskAssessment,
  SimilarCaseResponse,
  Workplace,
  WorkplaceRequest,
} from "../types/safety";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  status: number;
  fields: Record<string, string>;

  constructor(status: number, message: string, fields: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fields = fields;
  }
}

async function readError(response: Response) {
  const fallback = response.status === 403
    ? "로그인이 만료되었습니다. 다시 로그인해 주세요."
    : "요청을 처리하지 못했습니다.";

  try {
    const body = (await response.json()) as ApiErrorBody;
    return new ApiError(response.status, body.error || body.message || fallback, body.fields);
  } catch {
    return new ApiError(response.status, fallback);
  }
}

async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const authorization = getAuthorizationHeader();
  const headers = new Headers(init.headers);
  if (authorization) headers.set("Authorization", authorization);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  headers.set("ngrok-skip-browser-warning", "true");

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, "백엔드 서버에 연결하지 못했습니다. 서버가 실행 중인지 확인해 주세요.");
  }

  if (!response.ok) {
    if (response.status === 403) {
      clearAuthToken();
      window.location.assign(`${import.meta.env.BASE_URL}login`);
    }
    throw await readError(response);
  }

  return response.json() as Promise<T>;
}

export const safetyApi = {
  getMe: () => apiFetch<Me>("/api/auth/me"),

  createWorkplace: (request: WorkplaceRequest) =>
    apiFetch<Workplace>("/api/workplaces", { method: "POST", body: JSON.stringify(request) }),

  listWorkplaces: () => apiFetch<Workplace[]>("/api/workplaces"),

  getWorkplace: (workplaceId: number) => apiFetch<Workplace>(`/api/workplaces/${workplaceId}`),

  updateWorkplace: (workplaceId: number, request: WorkplaceRequest) =>
    apiFetch<Workplace>(`/api/workplaces/${workplaceId}`, { method: "PUT", body: JSON.stringify(request) }),

  getPreventionGuide: (workplace: Pick<Workplace, "industry" | "sizeClass" | "region">) => {
    const params = new URLSearchParams({
      industry: workplace.industry,
      sizeClass: workplace.sizeClass,
      region: workplace.region,
      expectedAccidentCount: "3",
      itemsPerAccident: "3",
    });
    return apiFetch<PreventionGuideResponse>(`/api/prevention-guide?${params}`);
  },

  getDiagnosisPreventionGuide: (workplaceId: number) =>
    apiFetch<PreventionGuideResponse>(`/api/workplaces/${workplaceId}/prevention-guide`),

  getReferences: () => apiFetch<ReferenceData>("/api/references"),

  getChecklistItems: (workplaceId: number, criticalOnly = true, riskScopes: RiskScopeCode[] = [], limit = 35) => {
    const params = new URLSearchParams({ criticalOnly: String(criticalOnly), limit: String(limit) });
    riskScopes.forEach((scope) => params.append("scope", scope));
    return apiFetch<ChecklistItem[]>(`/api/workplaces/${workplaceId}/checklist-items?${params}`);
  },

  submitChecklist: (workplaceId: number, responses: ChecklistResponse[]) =>
    apiFetch<ChecklistSubmitResponse>(`/api/workplaces/${workplaceId}/checklist-submissions`, {
      method: "POST",
      body: JSON.stringify({ responses }),
    }),

  getLatestRiskAssessment: (workplaceId: number) =>
    apiFetch<RiskAssessment>(`/api/workplaces/${workplaceId}/risk-assessments/latest`),

  getSimilarCases: (workplaceId: number, size = 5) =>
    apiFetch<SimilarCaseResponse>(`/api/workplaces/${workplaceId}/similar-cases?size=${size}`),

  searchLaws: (query: string, size = 5) => {
    const params = new URLSearchParams({ q: query, size: String(size) });
    return apiFetch<LawSearchResponse>(`/api/laws/search?${params}`);
  },

  createChatSession: (workplaceId?: number) =>
    apiFetch<ChatSession>("/api/chat/sessions", {
      method: "POST",
      body: JSON.stringify(workplaceId ? { workplaceId } : {}),
    }),

  listChatSessions: () => apiFetch<ChatSession[]>("/api/chat/sessions"),

  askChat: (sessionId: string, question: string) =>
    apiFetch<AskResponse>(`/api/chat/sessions/${sessionId}/messages`, {
      method: "POST",
      body: JSON.stringify({ question }),
    }),

  getChatMessages: (sessionId: string) =>
    apiFetch<ChatMessage[]>(`/api/chat/sessions/${sessionId}/messages`),

  getAccidentResponse: (accidentType: string, industry: string) => {
    const params = new URLSearchParams({ accidentType, industry });
    return apiFetch<AccidentResponseGuide>(`/api/accident-response?${params}`);
  },

  consultAccident: (request: AccidentConsultRequest) =>
    apiFetch<AccidentConsultResponse>("/api/accident-response/consult", {
      method: "POST",
      body: JSON.stringify(request),
    }),

  createReport: (workplaceId: number) =>
    apiFetch<ReportCreateResponse>(`/api/workplaces/${workplaceId}/reports`, { method: "POST" }),

  downloadReport: async (reportId: number) => {
    let response: Response;
    try {
      response = await fetch(`${API_BASE}/api/reports/${reportId}/download`, {
        headers: {
          Authorization: getAuthorizationHeader(),
          "ngrok-skip-browser-warning": "true",
        },
      });
    } catch {
      throw new ApiError(0, "PDF를 내려받지 못했습니다. 서버 연결을 확인해 주세요.");
    }
    if (!response.ok) {
      if (response.status === 403) {
        clearAuthToken();
        window.location.assign(`${import.meta.env.BASE_URL}login`);
      }
      throw await readError(response);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `안전관리_진단_리포트_${reportId}.pdf`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  },
};
