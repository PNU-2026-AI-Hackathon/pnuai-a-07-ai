#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_chunks.py — law_article → law_chunk (RAG 검색 단위 생성)
SafeWork AI / 데이터·DB 파트

하는 일
  - law_article 각 행(조 또는 항)을 청크로 변환
  - 청크 앞머리에 출처 헤더를 붙임:  [법령명 제N조(제목) 제M항]
  - 조문이 너무 길면(기본 600자) 호(號)/줄 단위로 나누고 헤더를 반복 삽입
  - law_chunk 에 UNIQUE(article_id, chunk_index) 로 적재

설계 결정 (중요)
  - "청크 1개 = law_article 행 1개" 를 원칙으로 한다(길면 분할).
    작업안내문 4.1절의 '짧은 항 병합' 규칙은 채택하지 않는다.
    이유: law_chunk.article_id 는 단일 FK 라, 두 항을 한 청크로 합치면
    어느 조항을 인용한 것인지 모호해진다. 법률 RAG 는 인용 정확도가 우선이므로
    조·항 경계를 그대로 유지한다.

실행
  export PGHOST=localhost PGDATABASE=ai_safework PGUSER=postgres PGPASSWORD=...
  python build_chunks.py --preview     # DB 안 건드리고 샘플 10개만 출력
  python build_chunks.py               # 실제 적재 (law_chunk 재생성)

주의
  - 실행 시 law_chunk 를 전부 비우고 다시 만든다(TRUNCATE).
    아직 임베딩(indexed_at) 전 단계라 안전하다.
    임베딩을 시작한 뒤에는 이 스크립트를 그냥 돌리면 faiss_idx 가 날아가니
    반드시 이승원(ML)과 협의 후 실행할 것.
"""

import argparse
import os
import sys

MAX_CHARS = 600      # 이 길이를 넘으면 분할 시도
HARD_CAP = 1200      # 줄 단위로도 못 나눌 때 강제로 자르는 상한


# ------------------------------------------------------------------
# 헤더 생성:  [산업안전보건법 제38조(안전조치) 제1항]
# ------------------------------------------------------------------
def make_header(law_name, article_no, title, clause_no):
    head = law_name or ""
    if article_no:
        head += f" {article_no}"
    if title:
        head += f"({title})"
    if clause_no:
        head += f" {clause_no}"
    return f"[{head.strip()}]"


# ------------------------------------------------------------------
# 핵심: law_article 한 행 → 청크 텍스트 리스트 (순수 함수, DB 무관)
# ------------------------------------------------------------------
def build_chunks_for_article(row):
    """
    row: dict(law_name, article_no, clause_no, title, content)
    반환: [청크텍스트, ...]  (헤더 포함)
    """
    header = make_header(row.get("law_name"), row.get("article_no"),
                         row.get("title"), row.get("clause_no"))
    content = (row.get("content") or "").strip()

    if not content:
        # 내용이 비면 제목만이라도 청크로 (조 머리글 등)
        return [header]

    full = f"{header}\n{content}"
    if len(full) <= MAX_CHARS:
        return [full]

    # 길다 → 줄(호) 단위로 누적 분할, 각 조각에 헤더 반복
    lines = content.split("\n")
    chunks, buf = [], ""
    for line in lines:
        line = line.rstrip()
        if not line:
            continue
        # 한 줄 자체가 상한을 넘으면 문자 단위로 강제 분할
        if len(line) > HARD_CAP:
            for i in range(0, len(line), HARD_CAP):
                piece = line[i:i + HARD_CAP]
                chunks.append(f"{header}\n{piece}")
            continue
        candidate = (buf + "\n" + line).strip() if buf else line
        if len(header) + 1 + len(candidate) > MAX_CHARS and buf:
            chunks.append(f"{header}\n{buf.strip()}")
            buf = line
        else:
            buf = candidate
    if buf.strip():
        chunks.append(f"{header}\n{buf.strip()}")

    # 분할된 청크가 여러 개면 (n/전체) 표시로 문맥 보강
    if len(chunks) > 1:
        total = len(chunks)
        chunks = [c.replace(header, f"{header} ({i+1}/{total})", 1)
                  for i, c in enumerate(chunks)]
    return chunks


# ------------------------------------------------------------------
# DB
# ------------------------------------------------------------------
def connect():
    import psycopg2
    return psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"),
        port=os.getenv("PGPORT", "5432"),
        dbname=os.getenv("PGDATABASE", "ai_safework"),
        user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", ""),
    )


FETCH_SQL = """
SELECT article_id, law_name, article_no, clause_no, title, content
FROM   law_article
ORDER  BY article_id;
"""

INSERT_SQL = """
INSERT INTO law_chunk (article_id, chunk_index, content)
VALUES (%(article_id)s, %(chunk_index)s, %(content)s)
ON CONFLICT (article_id, chunk_index)
DO UPDATE SET content = EXCLUDED.content;
"""


def run(preview):
    try:
        import psycopg2  # noqa
    except ImportError:
        sys.exit("psycopg2 미설치: pip install psycopg2-binary\n"
                 "(python 이 아니라 py 또는 python.exe 전체경로로 실행해야 할 수 있음)")

    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.execute(FETCH_SQL)
            cols = [d[0] for d in cur.description]
            articles = [dict(zip(cols, r)) for r in cur.fetchall()]
        print(f"law_article {len(articles)}행 로드")

        # 청크 생성
        chunk_rows = []
        long_count = 0
        for a in articles:
            texts = build_chunks_for_article(a)
            if len(texts) > 1:
                long_count += 1
            for idx, t in enumerate(texts):
                chunk_rows.append({"article_id": a["article_id"],
                                   "chunk_index": idx, "content": t})
        print(f"생성 청크 {len(chunk_rows)}개 "
              f"(분할 발생 조문 {long_count}개)")

        if preview:
            print("\n===== 미리보기 (앞 10개) =====")
            for c in chunk_rows[:10]:
                print(f"\n--- article_id={c['article_id']} "
                      f"idx={c['chunk_index']} len={len(c['content'])} ---")
                print(c["content"][:300])
            print("\n--preview: DB 미적재.")
            return

        with conn, conn.cursor() as cur:
            import psycopg2.extras
            cur.execute("TRUNCATE law_chunk RESTART IDENTITY;")
            psycopg2.extras.execute_batch(cur, INSERT_SQL, chunk_rows,
                                          page_size=200)
            cur.execute("SELECT count(*) FROM law_chunk;")
            total = cur.fetchone()[0]
        print(f"\n적재 완료. law_chunk 총 {total}개.")
        print("다음: 이승원(ML)이 indexed_at IS NULL 청크를 임베딩 → FAISS")
    finally:
        conn.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true",
                    help="DB 적재 없이 샘플 청크만 출력")
    args = ap.parse_args()
    run(args.preview)


if __name__ == "__main__":
    main()
