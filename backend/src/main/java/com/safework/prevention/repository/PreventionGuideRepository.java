package com.safework.prevention.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PreventionGuideRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String CALL_FUNCTION =
            "SELECT * FROM fn_prevention_guide(?, ?, ?, ?, ?)";

    public List<PreventionGuideRow> fetch(String industry, String sizeClass, String region,
                                           int expectedAccidentCount, int itemsPerAccident) {
        return jdbcTemplate.query(CALL_FUNCTION,
                (rs, rowNum) -> new PreventionGuideRow(
                        rs.getInt("accident_rank"),
                        rs.getString("accident_type"),
                        rs.getBigDecimal("accident_ratio"),
                        rs.getBigDecimal("death_ratio"),
                        rs.getString("item_code"),
                        rs.getString("work_type"),
                        rs.getString("question"),
                        rs.getBigDecimal("risk_weight"),
                        rs.getBoolean("is_critical"),
                        toLawBasisList(rs.getObject("law_basis"))
                ),
                industry, sizeClass, region, expectedAccidentCount, itemsPerAccident);
    }

    private List<String> toLawBasisList(Object lawBasis) {
        if (lawBasis == null) {
            return List.of();
        }
        if (lawBasis instanceof Array sqlArray) {
            try {
                return Arrays.stream((Object[]) sqlArray.getArray())
                        .map(String::valueOf)
                        .toList();
            } catch (SQLException e) {
                throw new IllegalStateException("law_basis 배열을 읽는 중 오류가 발생했습니다.", e);
            }
        }
        return List.of(String.valueOf(lawBasis));
    }
}
