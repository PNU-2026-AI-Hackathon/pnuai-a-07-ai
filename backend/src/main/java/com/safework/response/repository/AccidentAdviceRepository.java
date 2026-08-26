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
     * 판례와 지원사업.
     *
     * 어느 판례가 이 재해유형과 가까운지, 어떤 지원사업이 사업주에게 해당하는지 고르는
     * 규칙이 함수 안에 있어 그대로 쓴다. 행정 계층만 따로 읽는다(위 참고).
     *
     * <p>한때 정책 계층에 "사과 기상 재해예방"(장수군 · 농림축산어업) 같은 게 섞여 나와
     * 백엔드에서 걸러 냈는데, SCHEMA_24 에서 함수가 분야(field) 조건을 갖게 되어
     * 우회 코드를 걷어냈다. 같은 규칙을 두 곳에 두면 언젠가 어긋난다.
     */
    private static final String ADVICE = """
            SELECT layer, title, reason, detail, agency, reference, url
            FROM   fn_accident_advice(?, ?, ?)
            WHERE  layer <> '행정'
            ORDER  BY layer DESC, priority
            """;

    /**
     * 중대재해 해당 여부 판정.
     *
     * 예전에는 기준 3가지를 백엔드에 적어 두고 "직접 대조하세요"로 안내했는데,
     * DB 파트가 기준을 표로 만들고 판정 함수까지 붙여 줬다(SCHEMA_25).
     * 사망자·부상자 수를 알면 이제 자동으로 판정하고 어떤 기준에 걸렸는지도 알려준다.
     */
    private static final String CHECK_SEVERE = """
            SELECT is_severe, matched FROM fn_check_severe(?, ?, ?)
            """;

    /** 중대재해 판단 기준. 사용자가 직접 대조할 수 있게 그대로 보여 준다. */
    private static final String SEVERE_CRITERIA = """
            SELECT description, legal_basis
            FROM   severe_accident_criteria
            ORDER  BY criteria_id
            """;

    public record SevereCheck(boolean severe, List<String> matched) {
    }

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

    /** fn_accident_advice 의 한 행. layer 는 '법률'(판례) 또는 '정책'(지원사업). */
    public record Advice(
            String layer,
            String title,
            /** 이 사고와 어떤 점이 닿아 있는지 */
            String reason,
            String detail,
            /** 법원 또는 주관 기관 */
            String agency,
            /** 사건번호·선고일 또는 신청 기한 */
            String reference,
            String url
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

    /** 사망·중상·부상자 수로 중대재해 여부를 판정한다. 모르는 값은 0 으로 넘긴다. */
    public SevereCheck checkSevere(int death, int seriousInjury, int injuryOrDisease) {
        return jdbcTemplate.queryForObject(CHECK_SEVERE,
                (rs, i) -> {
                    var matched = rs.getArray("matched");
                    List<String> labels = matched == null ? List.of()
                            : List.of((String[]) matched.getArray());
                    return new SevereCheck(rs.getBoolean("is_severe"), labels);
                },
                death, seriousInjury, injuryOrDisease);
    }

    /** 중대재해 판단 기준 문구. 근거 조문은 어느 행이나 같아 첫 행 것을 쓴다. */
    public List<String> findSevereCriteria() {
        return jdbcTemplate.query(SEVERE_CRITERIA, (rs, i) -> rs.getString("description"));
    }

    public String findSevereCriteriaBasis() {
        return jdbcTemplate.query(SEVERE_CRITERIA, (rs, i) -> rs.getString("legal_basis"))
                .stream().findFirst().orElse(null);
    }

    public List<Advice> findAdvice(String industry, String accidentType, boolean severe) {
        return jdbcTemplate.query(ADVICE,
                (rs, i) -> new Advice(
                        rs.getString("layer"),
                        rs.getString("title"),
                        rs.getString("reason"),
                        rs.getString("detail"),
                        rs.getString("agency"),
                        rs.getString("reference"),
                        rs.getString("url")),
                industry, accidentType, severe);
    }
}
