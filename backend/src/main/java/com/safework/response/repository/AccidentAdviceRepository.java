package com.safework.response.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 사고가 난 뒤 밟아야 할 행정 절차와, 참고할 판례·지원사업.
 *
 * <p>DB 파트가 SCHEMA_10(admin_procedure) 과 SCHEMA_14(fn_accident_advice) 로 이미
 * 정리해 둔 것을 그대로 쓴다. 예전에는 백엔드가 조문만 보고 손으로 적어 두었는데,
 * 그 방식으로는 <b>서식 다운로드 링크·담당 기관·과태료 금액</b>을 줄 수 없었다.
 * (법령 본문에는 금액이 없고 admin_procedure.penalty 에 있다)
 *
 * <p>행정 절차는 구조가 필요해서 표를 직접 읽고, 판례·정책은 "이 재해유형과 얼마나
 * 가까운지" 고르는 규칙이 함수 안에 있어 함수를 그대로 호출한다.
 */
@Repository
@RequiredArgsConstructor
public class AccidentAdviceRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 사고 후 행정 절차.
     *
     * 필터는 fn_accident_advice 의 행정 계층과 같은 조건이다
     * ({@code is_active AND (중대재해 OR NOT is_critical_only)}).
     * 함수를 쓰지 않고 표를 읽는 이유는, 함수가 기한과 조치 내용을 한 문자열로 합쳐 주기
     * 때문이다. 화면에서 기한·기관·서식을 따로 보여주려면 컬럼이 나뉘어 있어야 한다.
     */
    private static final String PROCEDURES = """
            SELECT proc_code, category, title, action_summary, deadline_text, agency,
                   form_name, form_url, legal_basis, penalty, disclaimer, priority
            FROM   admin_procedure
            WHERE  is_active AND (? OR NOT is_critical_only)
            ORDER  BY priority, procedure_id
            """;

    /**
     * 이 사고와 닮은 판례. "재해유형이 판결문에 나오는가"로 고르는 규칙이 함수 안에 있어
     * 그대로 쓴다(행정·정책 계층은 따로 읽으므로 제외).
     */
    private static final String PRECEDENTS = """
            SELECT title, reason, detail, agency, reference, url
            FROM   fn_accident_advice(?, ?, ?)
            WHERE  layer = '법률'
            ORDER  BY priority
            """;

    /**
     * 재발방지에 쓸 수 있는 사업주 지원사업.
     *
     * fn_accident_advice 의 정책 계층과 조건이 거의 같지만 <b>분야(field) 조건을 하나 더</b>
     * 건다. 함수는 제목에 '예방' 이 들어가면 통과시키는데, 그러면 "사과 기상 재해예방"
     * (장수군 · 농림축산어업) 같은 게 공장 사망사고 안내에 섞여 나온다.
     * 실제로 확인했고, 산재와 관련된 세 분야로 좁히면 나머지 9건은 그대로 남는다.
     *
     * 업종이 대상·내용에 언급된 사업을 먼저 보여준다.
     */
    private static final String PROGRAMS = """
            SELECT title, agency, summary, support_type, apply_deadline, detail_url,
                   (target ILIKE '%%' || ? || '%%' OR content ILIKE '%%' || ? || '%%') AS industry_match
            FROM   policy_service
            WHERE  is_employer
              AND  field IN ('행정·안전', '고용·창업', '보건·의료')
              AND  title ~ '예방|융자|요율|컨설팅|상생|안전보건관리|대체인력|직장복귀|원직장'
            ORDER  BY industry_match DESC,
                      CASE WHEN title ~ '예방|융자|요율|컨설팅|상생|안전보건관리' THEN 1 ELSE 2 END,
                      service_id
            LIMIT  ?
            """;

    public record Procedure(
            String code,
            String category,
            String title,
            String actionSummary,
            String deadline,
            String agency,
            String formName,
            String formUrl,
            String legalBasis,
            String penalty,
            String disclaimer,
            int priority
    ) {
    }

    public record Precedent(
            String caseName,
            /** 이 사고와 어떤 점이 닮았는지 */
            String relevance,
            String summary,
            String court,
            /** 사건번호 · 선고일 */
            String reference,
            String url
    ) {
    }

    public record Program(
            String title,
            String agency,
            String summary,
            String supportType,
            String deadline,
            String url,
            /** 이 업종이 대상·내용에 언급됐는지 */
            boolean industryMatch
    ) {
    }

    public List<Procedure> findProcedures(boolean severe) {
        return jdbcTemplate.query(PROCEDURES,
                (rs, i) -> new Procedure(
                        rs.getString("proc_code"),
                        rs.getString("category"),
                        rs.getString("title"),
                        rs.getString("action_summary"),
                        rs.getString("deadline_text"),
                        rs.getString("agency"),
                        rs.getString("form_name"),
                        rs.getString("form_url"),
                        rs.getString("legal_basis"),
                        rs.getString("penalty"),
                        rs.getString("disclaimer"),
                        rs.getInt("priority")),
                severe);
    }

    public List<Precedent> findPrecedents(String industry, String accidentType, boolean severe) {
        return jdbcTemplate.query(PRECEDENTS,
                (rs, i) -> new Precedent(
                        rs.getString("title"),
                        rs.getString("reason"),
                        rs.getString("detail"),
                        rs.getString("agency"),
                        rs.getString("reference"),
                        rs.getString("url")),
                industry, accidentType, severe);
    }

    public List<Program> findSupportPrograms(String industry, int limit) {
        String keyword = industry == null ? "" : industry;
        return jdbcTemplate.query(PROGRAMS,
                (rs, i) -> new Program(
                        rs.getString("title"),
                        rs.getString("agency"),
                        rs.getString("summary"),
                        rs.getString("support_type"),
                        rs.getString("apply_deadline"),
                        rs.getString("detail_url"),
                        rs.getBoolean("industry_match")),
                keyword, keyword, limit);
    }
}
