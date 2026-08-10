import React, { createContext, useContext, useEffect, useState } from "react";
import type {
  Answer,
  ChecklistItem,
  Me,
  PreventionGuideResponse,
  RiskAssessment,
  Workplace,
} from "../types/safety";
import { previewChecklistItems, previewPreventionGuide, previewWorkplace } from "../utils/devPreview";

const SESSION_KEY = "safework_diagnosis_session";

interface DiagnosisSession {
  user: Me | null;
  workplace: Workplace | null;
  preventionGuide: PreventionGuideResponse | null;
  checklistItems: ChecklistItem[];
  checklistAnswers: Record<string, Answer>;
  riskAssessment: RiskAssessment | null;
}

interface SafetyContextType extends DiagnosisSession {
  setUser: (value: Me | null) => void;
  setWorkplace: (value: Workplace | null) => void;
  setPreventionGuide: (value: PreventionGuideResponse | null) => void;
  setChecklistItems: (value: ChecklistItem[]) => void;
  setChecklistAnswers: (value: Record<string, Answer>) => void;
  setRiskAssessment: (value: RiskAssessment | null) => void;
  startPreviewDiagnosis: () => void;
  resetDiagnosis: () => void;
}

const emptySession: DiagnosisSession = {
  user: null,
  workplace: null,
  preventionGuide: null,
  checklistItems: [],
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
      setChecklistAnswers: (value) => update("checklistAnswers", value),
      setRiskAssessment: (value) => update("riskAssessment", value),
      startPreviewDiagnosis: () => {
        if (import.meta.env.DEV) setSession({ ...emptySession, workplace: previewWorkplace, preventionGuide: previewPreventionGuide, checklistItems: previewChecklistItems });
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
