package com.safework.reference.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 프론트가 셀렉트박스에 쓸 코드값.
 *
 * <p>DB 파트가 SCHEMA_8 에서 <code>v_ref_*</code> 뷰를 만들어 두었다. 주석에
 * "프런트가 값을 하드코딩하지 않고 DB 값을 그대로 사용하게 함"이라고 적혀 있는데,
 * 정작 그걸 내보내는 엔드포인트가 없어서 문서에 목록을 적어 두고 있었다.
 * 코드값이 바뀌면 문서와 화면이 같이 낡으므로 뷰를 그대로 API 로 연다.
 *
 * <p>테이블이 아니라 뷰를 읽는 이유는, 어떤 컬럼을 공개할지를 DB 파트가 정하도록
 * 두기 위해서다. 뷰의 정렬도 그대로 따른다(규모는 sort_order, 나머지는 코드순).
 */
@Repository
@RequiredArgsConstructor
public class ReferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Industry(String code, String displayName, boolean highRisk) {
    }

    public record SizeClass(String code, String displayName, String modelSizeClass, int sortOrder) {
    }

    public record Region(String code, String displayName, boolean target) {
    }

    public record AccidentType(String code, String displayName) {
    }

    public record WorkType(String industry, String workType, int itemCount) {
    }

    public List<Industry> findIndustries() {
        return jdbcTemplate.query("SELECT code, display_name, is_high_risk FROM v_ref_industry",
                (rs, i) -> new Industry(rs.getString("code"), rs.getString("display_name"),
                        rs.getBoolean("is_high_risk")));
    }

    public List<SizeClass> findSizeClasses() {
        return jdbcTemplate.query(
                "SELECT code, display_name, model_size_class, sort_order FROM v_ref_size_class",
                (rs, i) -> new SizeClass(rs.getString("code"), rs.getString("display_name"),
                        rs.getString("model_size_class"), rs.getInt("sort_order")));
    }

    public List<Region> findRegions() {
        return jdbcTemplate.query("SELECT code, display_name, is_target FROM v_ref_region",
                (rs, i) -> new Region(rs.getString("code"), rs.getString("display_name"),
                        rs.getBoolean("is_target")));
    }

    /**
     * 재해유형은 뷰가 없어서 코드 테이블을 직접 읽는다.
     * 위험도 진단의 topAccidentType, 예방 가이드, 사고 대처가 모두 이 어휘를 쓴다.
     */
    public List<AccidentType> findAccidentTypes() {
        return jdbcTemplate.query(
                "SELECT accident_type, display_name FROM code_accident_type ORDER BY accident_type",
                (rs, i) -> new AccidentType(rs.getString("accident_type"),
                        rs.getString("display_name")));
    }

    /**
     * 업종별 작업 종류. 점검 문항이 업종당 수백 개라 화면에서 한 번에 다 물을 수 없어,
     * 프론트가 작업 종류로 먼저 좁힐 수 있도록 문항 수와 함께 준다.
     *
     * 뷰의 집계 컬럼 이름이 한글("문항수")이라 따옴표로 감싸야 한다.
     */
    public List<WorkType> findWorkTypes() {
        return jdbcTemplate.query(
                "SELECT target_industry, work_type, \"문항수\" AS item_count FROM v_ref_work_type",
                (rs, i) -> new WorkType(rs.getString("target_industry"), rs.getString("work_type"),
                        rs.getInt("item_count")));
    }
}
