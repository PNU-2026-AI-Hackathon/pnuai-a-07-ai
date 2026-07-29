-- ============================================================
-- law_article 인덱스 추가
-- 목적: law_name + article_no 기반 검색 성능 향상
--       checklist_item.law_ref(TEXT) → article_id JOIN 속도 개선
-- ============================================================

-- 법령명 + 조번호 복합 인덱스 (법령 조회 시 주요 검색 조건)
CREATE INDEX IF NOT EXISTS idx_law_article_name_no
    ON public.law_article (law_name, article_no);

-- article_id는 PK라 이미 인덱스 있음
-- law_name 단독 인덱스 (법령명으로 필터링 시)
CREATE INDEX IF NOT EXISTS idx_law_article_law_name
    ON public.law_article (law_name);

-- title 전문 검색용 트라이그램 인덱스 (pg_trgm 확장 필요 — 기존 SQL에 이미 설치됨)
CREATE INDEX IF NOT EXISTS idx_law_article_title_trgm
    ON public.law_article USING gin (title public.gin_trgm_ops);

-- content 전문 검색용 트라이그램 인덱스
CREATE INDEX IF NOT EXISTS idx_law_article_content_trgm
    ON public.law_article USING gin (content public.gin_trgm_ops);
