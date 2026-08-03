"""
유사 재해사례(sif_case) 임베딩 + FAISS 인덱스를 만드는 스크립트.

실행:
    cd mlserver
    .venv\\Scripts\\activate
    python scripts\\build_sif_index.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services import case_service

print("유사 재해사례 임베딩 인덱스 구축 시작...")
case_service.build_sif_index()
print("완료!")

print()
print("=== 검색 테스트 ===")
for industry, sub_industry in [("제조업", "금속가공"), ("건설업", "건축공사업")]:
    print(f"\n요청: industry={industry}, sub_industry={sub_industry}")
    result = case_service.analyze_similar_cases(industry, sub_industry, top_n=3)
    print("top_keywords:", result["top_keywords"])
    for c in result["similar_cases"]:
        print(f"  [{c['score']:.3f}] sif_id={c['sif_id']} {c['summary'][:70]}")
