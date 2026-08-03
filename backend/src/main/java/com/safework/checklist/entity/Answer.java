package com.safework.checklist.entity;

/**
 * checklist_response.answer 의 PostgreSQL enum(answer_t) 대응.
 * NA = 해당 없음 (위험도 계산에서 제외됨)
 */
public enum Answer {
    YES,
    NO,
    NA
}
