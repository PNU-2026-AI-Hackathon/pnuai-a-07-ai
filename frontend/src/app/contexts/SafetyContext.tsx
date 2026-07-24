import React, { createContext, useContext, useState } from "react";
import { BusinessData, RiskData, AccidentCase, ChecklistItem } from "../types/safety";

interface SafetyContextType {
  businessData: BusinessData | null;
  setBusinessData: (data: BusinessData) => void;
  riskData: RiskData | null;
  setRiskData: (data: RiskData) => void;
  accidentCases: AccidentCase[];
  setAccidentCases: (cases: AccidentCase[]) => void;
  checklist: ChecklistItem[];
  setChecklist: (items: ChecklistItem[]) => void;
  toggleChecklistItem: (id: string) => void;
}

const SafetyContext = createContext<SafetyContextType | undefined>(undefined);

export function SafetyProvider({ children }: { children: React.ReactNode }) {
  const [businessData, setBusinessData] = useState<BusinessData | null>(null);
  const [riskData, setRiskData] = useState<RiskData | null>(null);
  const [accidentCases, setAccidentCases] = useState<AccidentCase[]>([]);
  const [checklist, setChecklist] = useState<ChecklistItem[]>([]);
  
  const toggleChecklistItem = (id: string) => {
    setChecklist(prev => 
      prev.map(item => 
        item.id === id ? { ...item, checked: !item.checked } : item
      )
    );
  };
  
  return (
    <SafetyContext.Provider value={{
      businessData,
      setBusinessData,
      riskData,
      setRiskData,
      accidentCases,
      setAccidentCases,
      checklist,
      setChecklist,
      toggleChecklistItem,
    }}>
      {children}
    </SafetyContext.Provider>
  );
}

export function useSafety() {
  const context = useContext(SafetyContext);
  if (!context) {
    throw new Error("useSafety must be used within SafetyProvider");
  }
  return context;
}
