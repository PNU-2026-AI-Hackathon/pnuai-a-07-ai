import { BusinessData, RiskData, AccidentCase, ChecklistItem } from "../types/safety";

export const industries = [
  {
    name: "제조업",
    subIndustries: [
      "금속가공",
      "기계·설비 제조",
      "식품 제조",
      "화학·플라스틱",
      "섬유·의류",
      "전자·반도체",
      "자동차 부품",
    ],
  },
  {
    name: "건설업",
    subIndustries: [
      "건축공사",
      "토목공사",
      "설비 설치",
      "인테리어",
      "철거·해체",
    ],
  },
  {
    name: "서비스업",
    subIndustries: [
      "물류·창고",
      "음식·숙박",
      "청소·시설관리",
      "요양·돌봄",
      "기타 서비스",
    ],
  },
  {
    name: "농림어업",
    subIndustries: [
      "농업",
      "임업",
      "어업·양식",
    ],
  },
];

export function calculateRisk(data: BusinessData): RiskData {
  // Mock calculation based on industry and size
  let riskScore = 50;
  
  // Industry risk factors
  if (data.industry === "제조업") {
    if (data.subIndustry === "금속가공") riskScore += 20;
    else if (data.subIndustry === "화학·플라스틱") riskScore += 25;
    else riskScore += 15;
  } else if (data.industry === "건설업") {
    riskScore += 30;
  }
  
  // Size factor (smaller = higher risk due to less safety resources)
  if (data.employeeCount < 10) riskScore += 10;
  else if (data.employeeCount > 50) riskScore -= 10;
  
  riskScore = Math.min(100, Math.max(0, riskScore));
  
  let riskLevel: "safe" | "caution" | "danger";
  if (riskScore < 40) riskLevel = "safe";
  else if (riskScore < 70) riskLevel = "caution";
  else riskLevel = "danger";
  
  // Generate top accidents based on industry
  let topAccidents = [];
  if (data.industry === "제조업") {
    topAccidents = [
      { type: "끼임·협착", percentage: 42 },
      { type: "추락·낙하", percentage: 28 },
      { type: "화상·감전", percentage: 18 },
      { type: "기타", percentage: 12 },
    ];
  } else if (data.industry === "건설업") {
    topAccidents = [
      { type: "추락", percentage: 48 },
      { type: "낙하·비래", percentage: 25 },
      { type: "붕괴·도괴", percentage: 15 },
      { type: "기타", percentage: 12 },
    ];
  } else {
    topAccidents = [
      { type: "넘어짐", percentage: 35 },
      { type: "부딪힘", percentage: 30 },
      { type: "화재", percentage: 20 },
      { type: "기타", percentage: 15 },
    ];
  }
  
  const comparisonPercent = Math.floor((riskScore - 50) * 0.3);
  const comparisonText = comparisonPercent > 0 
    ? `동종 업계 평균보다 ${comparisonPercent}% 더 위험합니다`
    : `동종 업계 평균보다 ${Math.abs(comparisonPercent)}% 더 안전합니다`;
  
  return {
    overallRisk: riskScore,
    riskLevel,
    topAccidents,
    comparisonText,
  };
}

export function getAccidentCases(data: BusinessData): AccidentCase[] {
  const cases: AccidentCase[] = [];
  
  if (data.industry === "제조업" && data.subIndustry === "금속가공") {
    cases.push({
      id: "1",
      date: "2025.11.14",
      location: "경기도 시흥시 금속가공업체",
      cause: "프레스 기계 방호장치 미작동",
      result: "근로자 우측 손가락 절단 (중상)",
      fullDescription: "작업자가 프레스 기계로 금속판을 절단하던 중 양수조작식 방호장치가 고장난 상태에서 작업을 진행하다가 손가락이 프레스에 끼어 절단되는 사고가 발생했습니다. 사업주는 방호장치 고장 상태를 인지하고 있었으나 납기에 쫓겨 수리 없이 작업을 지시한 것으로 확인되었습니다.",
      aiAdvice: "사장님 공장과 유사한 프레스 기계에서 발생한 사고입니다. 방호장치를 꼭 확인하세요.",
    });
    
    cases.push({
      id: "2",
      date: "2025.09.22",
      location: "충남 천안시 금속부품 제조공장",
      cause: "연삭기 작업 중 보안경 미착용",
      result: "근로자 좌측 안구 손상",
      fullDescription: "연삭 작업 중 금속 파편이 눈으로 튀어들어 시력 손상을 입었습니다. 작업자는 불편하다는 이유로 보안경을 착용하지 않은 채 작업했으며, 관리감독자도 이를 제지하지 않았습니다.",
      aiAdvice: "소규모 사업장일수록 개인보호구 착용률이 낮습니다. 매일 아침 안전장구 착용 점검을 습관화하세요.",
    });
  } else if (data.industry === "건설업") {
    cases.push({
      id: "3",
      date: "2025.10.05",
      location: "서울시 강남구 건축 현장",
      cause: "안전난간 미설치 상태에서 작업",
      result: "근로자 3층에서 추락, 사망",
      fullDescription: "3층 슬라브 가장자리에서 철근 작업을 하던 근로자가 안전난간이 설치되지 않은 상태에서 발을 헛디뎌 추락했습니다. 안전대는 지급되었으나 부착 설비가 없어 착용하지 못한 상태였습니다.",
      aiAdvice: "추락은 건설 현장 사망사고의 50% 이상을 차지합니다. 안전난간 설치는 절대 미룰 수 없는 필수 조치입니다.",
    });
  } else {
    cases.push({
      id: "4",
      date: "2025.12.01",
      location: "인천시 물류센터",
      cause: "지게차 후진 중 사각지대 사고",
      result: "근로자 충돌로 다리 골절 (중상)",
      fullDescription: "지게차 운전자가 후진 중 사각지대에 있던 작업자를 발견하지 못하고 충돌하는 사고가 발생했습니다. 후방 감지 센서는 설치되어 있지 않았으며, 신호수도 배치되지 않았습니다.",
      aiAdvice: "지게차 사고는 좁은 공간에서 자주 발생합니다. 후방 카메라나 센서 설치를 검토하세요.",
    });
  }
  
  return cases;
}

export function getChecklist(data: BusinessData): ChecklistItem[] {
  const commonItems: ChecklistItem[] = [
    {
      id: "1",
      title: "산업안전보건법 상 필수 안전교육 실시",
      description: "근로자 정기 안전교육(분기별 6시간) 및 신규채용자 교육(8시간) 이수",
      actionLink: "https://www.kosha.or.kr",
      checked: false,
    },
    {
      id: "2",
      title: "개인보호구(PPE) 지급 및 착용 확인",
      description: "안전모, 안전화, 보안경 등 업무별 필수 보호구 지급 및 착용 상태 일일 점검",
      checked: false,
    },
    {
      id: "3",
      title: "작업장 내 비상구 및 소화기 확보",
      description: "비상구 2개소 이상 확보, 소화기 위치 표시 및 점검 기록 유지",
      checked: false,
    },
    {
      id: "4",
      title: "안전보건관리책임자 지정 (50인 이상 사업장)",
      description: "법정 의무사항으로 미이행 시 과태료 부과 대상",
      actionLink: "https://www.moel.go.kr",
      checked: false,
    },
  ];
  
  const industrySpecific: ChecklistItem[] = [];
  
  if (data.industry === "제조업") {
    industrySpecific.push(
      {
        id: "5",
        title: "프레스·전단기 방호장치 정상 작동 여부 확인",
        description: "양수조작식, 광전자식 등 방호장치 일일 점검 및 고장 시 즉시 수리",
        checked: false,
      },
      {
        id: "6",
        title: "연삭기·절단기 덮개 및 받침대 설치",
        description: "회전체 방호 덮개 설치 및 고정 상태 확인",
        checked: false,
      },
      {
        id: "7",
        title: "화학물질 MSDS(물질안전보건자료) 게시",
        description: "사용 화학물질 전체의 MSDS를 작업장 내 게시 및 교육 실시",
        checked: false,
      }
    );
  } else if (data.industry === "건설업") {
    industrySpecific.push(
      {
        id: "5",
        title: "추락방지 안전난간 및 안전망 설치",
        description: "2m 이상 높이 작업 시 안전난간 설치 또는 안전대 부착설비 구비",
        checked: false,
      },
      {
        id: "6",
        title: "굴착작업 시 흙막이 지보공 설치",
        description: "1.5m 이상 굴착 시 붕괴 방지를 위한 흙막이 및 지보공 설치",
        checked: false,
      },
      {
        id: "7",
        title: "크레인·리프트 정기 안전검사",
        description: "연 1회 이상 정밀안전검사 및 일일 작업 전 점검",
        checked: false,
      }
    );
  } else {
    industrySpecific.push(
      {
        id: "5",
        title: "지게차 후방 감지장치 또는 신호수 배치",
        description: "지게차 운행 구역 내 후방 카메라 또는 신호수 배치로 사각지대 사고 예방",
        checked: false,
      },
      {
        id: "6",
        title: "미끄럼 방지 조치 및 통로 확보",
        description: "주요 통로 및 계단 미끄럼 방지 테이프 부착, 적재물로 인한 통로 차단 금지",
        checked: false,
      }
    );
  }
  
  return [...commonItems, ...industrySpecific];
}