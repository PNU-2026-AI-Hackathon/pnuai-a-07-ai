"""
법령 임베딩 + FAISS 인덱스를 만드는 스크립트.

실행:
    cd mlserver
    .venv\\Scripts\\activate
    python scripts\\build_law_index.py

처음 실행하면 임베딩 모델(~440MB)을 인터넷에서 받아오기 때문에 몇 분 걸릴 수 있다.
끝나면 app/data/faiss/ 밑에 인덱스 파일이 생기고, 그 다음부터는 서버가 재시작해도
이 인덱스를 그대로 재사용한다 (다시 안 돌려도 됨).
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services import rag_service

print("법령 임베딩 인덱스 구축 시작...")
rag_service.build_law_index()
print("완료!")

print()
print("=== 검색 테스트 ===")
for query in ["추락 방지 조치", "컨베이어 끼임 예방", "LOTO 잠금 표지"]:
    print(f"\n질문: {query}")
    for r in rag_service.search_law(query, top_k=3):
        print(f"  [{r['score']:.3f}] {r['law_name']} {r['article_no']} - {r['content'][:60]}")
