package com.safework.risk.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 콜드스타트 위험도 진단은 DB 함수로 계산·저장한다(팀 합의 사항).
 * fn_coldstart_assess 는 사업장의 최신 체크리스트 제출을 기준으로 점수를 내고
 * risk_assessment 에 한 행을 넣은 뒤 assessment_id 를 돌려준다.
 */
@Repository
@RequiredArgsConstructor
public class ColdstartAssessRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long assess(Long workplaceId) {
        return jdbcTemplate.queryForObject(
                "SELECT fn_coldstart_assess(?)", Long.class, workplaceId);
    }
}
