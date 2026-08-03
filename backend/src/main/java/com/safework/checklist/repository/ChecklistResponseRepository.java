package com.safework.checklist.repository;

import com.safework.checklist.entity.Answer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * checklist_response 는 복합 PK(submission_id, item_id) + PostgreSQL enum(answer_t) 이고
 * 한 번에 수백 건이 들어가므로, JPA 엔티티 대신 배치 INSERT 로 처리한다.
 */
@Repository
@RequiredArgsConstructor
public class ChecklistResponseRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT = """
            INSERT INTO checklist_response (submission_id, item_id, answer, note)
            VALUES (?, ?, ?::answer_t, ?)
            """;

    public record ResponseRow(Long itemId, Answer answer, String note) {
    }

    public void saveAll(Long submissionId, List<ResponseRow> rows) {
        jdbcTemplate.batchUpdate(INSERT, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, submissionId);
            ps.setLong(2, row.itemId());
            ps.setString(3, row.answer().name());
            ps.setString(4, row.note());
        });
    }
}
