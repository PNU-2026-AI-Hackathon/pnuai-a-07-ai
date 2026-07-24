"""
checklist_item 20개 항목 정적 스냅샷 (2026-07-23, 강주호 SQL dump 기준).

fn_coldstart_score()의 체크리스트 가감점 계산(NO 응답 × risk_weight,
is_critical이면 2배 가중)에 그대로 쓰인다. PG 접속정보가 확정되면
이 상수 대신 checklist_item 테이블을 직접 조회하도록 바꿀 것 —
그 전까지는 이 파일이 유일한 출처이므로 DB가 바뀌면 함께 갱신해야 한다.
"""

from typing import TypedDict


class ChecklistItem(TypedDict):
    item_id: int
    item_code: str
    category: str
    question: str
    target_industry: str
    risk_weight: float
    is_critical: bool
    law_ref: str
    display_order: int


CHECKLIST_ITEMS: list[ChecklistItem] = [
    {"item_id": 1, "item_code": "CON-FALL-SCAF", "category": "추락",
     "question": "비계 작업발판에 안전난간(상부·중간)을 설치했습니까?",
     "target_industry": "건설업", "risk_weight": 3.00, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제42조(추락의 방지)", "display_order": 10},
    {"item_id": 2, "item_code": "CON-FALL-ROOF", "category": "추락",
     "question": "노후·채광판 지붕 작업 시 안전덮개(발판)와 추락방호망을 설치했습니까?",
     "target_industry": "건설업", "risk_weight": 3.00, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제45조(지붕 위에서의 위험 방지)", "display_order": 11},
    {"item_id": 3, "item_code": "CON-FALL-LADDER", "category": "추락",
     "question": "사다리 대신 작업발판·고소작업대를 사용하거나 사다리 안전수칙을 준수합니까?",
     "target_industry": "건설업", "risk_weight": 2.50, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙", "display_order": 12},
    {"item_id": 4, "item_code": "CON-FALL-STEEL", "category": "추락",
     "question": "철골·보 상부 작업 시 작업발판·고소작업대와 안전대를 사용합니까?",
     "target_industry": "건설업", "risk_weight": 2.50, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙 제42조(추락의 방지)", "display_order": 13},
    {"item_id": 5, "item_code": "CON-FALL-EDGE", "category": "추락",
     "question": "슬래브·작업발판 단부에 안전난간 또는 안전방망을 설치했습니까?",
     "target_industry": "건설업", "risk_weight": 3.00, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제42조(추락의 방지)", "display_order": 14},
    {"item_id": 6, "item_code": "CON-FALL-OPEN", "category": "추락",
     "question": "바닥·벽 개구부에 덮개 또는 안전난간을 견고하게 설치했습니까?",
     "target_industry": "건설업", "risk_weight": 3.00, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제43조(개구부 등의 방호 조치)", "display_order": 15},
    {"item_id": 7, "item_code": "CON-FALL-MEWP", "category": "추락",
     "question": "고소작업대 작업 시 안전난간과 안전대 부착설비를 갖추고 착용합니까?",
     "target_industry": "건설업", "risk_weight": 2.50, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙 제42조(추락의 방지)", "display_order": 16},
    {"item_id": 8, "item_code": "CON-FALL-SUSP", "category": "추락",
     "question": "달비계 로프를 2개 이상 견고한 고정점에 결속하고 작업 전 점검합니까?",
     "target_industry": "건설업", "risk_weight": 2.50, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙", "display_order": 17},
    {"item_id": 9, "item_code": "CON-FALL-MOBILE", "category": "추락",
     "question": "이동식비계 작업발판·단부 안전난간·바퀴 고정(아웃트리거)을 했습니까?",
     "target_industry": "건설업", "risk_weight": 2.00, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙", "display_order": 18},
    {"item_id": 10, "item_code": "CON-COLLAPSE-SOIL", "category": "붕괴",
     "question": "굴착작업 시 굴착면 기울기 기준을 준수하고 흙막이·사전조사를 했습니까?",
     "target_industry": "건설업", "risk_weight": 2.50, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙", "display_order": 19},
    {"item_id": 11, "item_code": "MFG-LOTO-MAINT", "category": "끼임",
     "question": "정비·수리·교체 등 비정형 작업 시 전원 차단 후 잠금·표지(LOTO)를 합니까?",
     "target_industry": "제조업", "risk_weight": 3.00, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제92조(정비 등의 작업 시의 운전정지)", "display_order": 20},
    {"item_id": 12, "item_code": "MFG-CLEAN-STOP", "category": "끼임",
     "question": "이물질 제거·청소 시 설비를 정지한 후 작업합니까?",
     "target_industry": "제조업", "risk_weight": 2.50, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제92조(정비 등의 작업 시의 운전정지)", "display_order": 21},
    {"item_id": 13, "item_code": "MFG-INSPECT-GUARD", "category": "끼임",
     "question": "설비 점검 시 안전장치를 유지하고 비상정지장치가 정상 작동합니까?",
     "target_industry": "제조업", "risk_weight": 2.50, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙", "display_order": 22},
    {"item_id": 14, "item_code": "MFG-CHEM-MSDS", "category": "화학물질",
     "question": "위험물질 취급 장소에 MSDS 비치·환기설비·보호구를 갖췄습니까?",
     "target_industry": "제조업", "risk_weight": 2.00, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙", "display_order": 23},
    {"item_id": 15, "item_code": "MFG-CRANE", "category": "부딪힘",
     "question": "크레인 사용 시 방호장치(과부하방지 등)와 신호수를 운용합니까?",
     "target_industry": "제조업", "risk_weight": 2.50, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제134조(방호장치의 조정)", "display_order": 24},
    {"item_id": 16, "item_code": "MFG-FORKLIFT", "category": "부딪힘",
     "question": "지게차에 후진경보·헤드가드·좌석안전띠를 갖추고 유도자를 배치합니까?",
     "target_industry": "제조업", "risk_weight": 2.50, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제179조(전조등 등)", "display_order": 25},
    {"item_id": 17, "item_code": "MFG-CONVEYOR", "category": "끼임",
     "question": "컨베이어에 방호울과 비상정지장치를 설치했습니까?",
     "target_industry": "제조업", "risk_weight": 2.50, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제191조(비상정지장치)", "display_order": 26},
    {"item_id": 18, "item_code": "MFG-WELD-FIRE", "category": "화재폭발",
     "question": "용접·절단 작업 시 화기감시자·소화기를 배치하고 가연물을 제거합니까?",
     "target_industry": "제조업", "risk_weight": 2.00, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙 제241조(화재위험작업 시의 준수사항)", "display_order": 27},
    {"item_id": 19, "item_code": "MFG-ROBOT", "category": "끼임",
     "question": "산업용로봇 작업영역에 방책(울)·인터록을 설치했습니까?",
     "target_industry": "제조업", "risk_weight": 2.50, "is_critical": True,
     "law_ref": "산업안전보건기준에 관한 규칙 제223조(운전 중 위험 방지)", "display_order": 28},
    {"item_id": 20, "item_code": "MFG-PASSAGE", "category": "전도",
     "question": "통로·작업장 바닥을 정리정돈하고 안전통로를 확보했습니까?",
     "target_industry": "제조업", "risk_weight": 1.50, "is_critical": False,
     "law_ref": "산업안전보건기준에 관한 규칙 제22조(통로의 설치)", "display_order": 29},
]

CHECKLIST_ITEM_BY_CODE: dict[str, ChecklistItem] = {item["item_code"]: item for item in CHECKLIST_ITEMS}
