import React, { createContext, useContext, useEffect, useState } from "react";
import type {
  Answer,
  ChecklistItem,
  Me,
  PreventionGuideResponse,
  RiskScopeCode,
  RiskAssessment,
  Workplace,
} from "../types/safety";
import { createPreviewChecklistItems, previewPreventionGuide, previewWorkplace } from "../utils/devPreview";

const SESSION_KEY = "safework_diagnosis_session";

interface DiagnosisSession {
  user: Me | null;
  workplace: Workplace | null;
  preventionGuide: PreventionGuideResponse | null;
  checklistItems: ChecklistItem[];
  selectedRiskScopes: RiskScopeCode[];
  checklistAnswers: Record<string, Answer>;
  riskAssessment: RiskAssessment | null;
}

interface SafetyContextType extends DiagnosisSession {
  setUser: (value: Me | null) => void;
  setWorkplace: (value: Workplace | null) => void;
  setPreventionGuide: (value: PreventionGuideResponse | null) => void;
  setChecklistItems: (value: ChecklistItem[]) => void;
  setSelectedRiskScopes: (value: RiskScopeCode[]) => void;
  setChecklistAnswers: (value: Record<string, Answer>) => void;
  setRiskAssessment: (value: RiskAssessment | null) => void;
  startPreviewDiagnosis: (riskScopes: RiskScopeCode[], workplace?: Workplace) => void;
  resetDiagnosis: () => void;
}

const emptySession: DiagnosisSession = {
  user: null,
  workplace: null,
  preventionGuide: null,
  checklistItems: [],
  selectedRiskScopes: [],
  checklistAnswers: {},
  riskAssessment: null,
};

function readSession(): DiagnosisSession {
  try {
    const stored = sessionStorage.getItem(SESSION_KEY);
    return stored ? { ...emptySession, ...JSON.parse(stored) } : emptySession;
  } catch {
    return emptySession;
  }
}

const SafetyContext = createContext<SafetyContextType | undefined>(undefined);

export function SafetyProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<DiagnosisSession>(readSession);

  useEffect(() => {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  }, [session]);

  const update = <K extends keyof DiagnosisSession>(key: K, value: DiagnosisSession[K]) =>
    setSession((current) => ({ ...current, [key]: value }));

  return (
    <SafetyContext.Provider value={{
      ...session,
      setUser: (value) => update("user", value),
      setWorkplace: (value) => update("workplace", value),
      setPreventionGuide: (value) => update("preventionGuide", value),
      setChecklistItems: (value) => update("checklistItems", value),
      setSelectedRiskScopes: (value) => update("selectedRiskScopes", value),
      setChecklistAnswers: (value) => update("checklistAnswers", value),
      setRiskAssessment: (value) => update("riskAssessment", value),
      startPreviewDiagnosis: (riskScopes, workplace) => {
        if (import.meta.env.DEV) setSession({ ...emptySession, workplace: workplace ?? previewWorkplace, preventionGuide: previewPreventionGuide, checklistItems: createPreviewChecklistItems(riskScopes), selectedRiskScopes: riskScopes });
      },
      resetDiagnosis: () => setSession(emptySession),
    }}>
      {children}
    </SafetyContext.Provider>
  );
}

export function useSafety() {
  const context = useContext(SafetyContext);
  if (!context) throw new Error("useSafety must be used within SafetyProvider");
  return context;
}
