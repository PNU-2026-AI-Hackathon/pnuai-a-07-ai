package com.safework.global.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // PostgreSQL SQLSTATE. 값 자체보다 "사용자가 고칠 수 있는 오류인가"로 나눈 것이다.
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";

    private static final Pattern OFFENDING_COLUMN = Pattern.compile("Key \\(([^)]+)\\)=");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "잘못된 값입니다." : error.getDefaultMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new));

        return ResponseEntity.badRequest()
                .body(Map.of("error", "입력값이 올바르지 않습니다.", "fields", fieldErrors));
    }

    /** 필수 쿼리 파라미터가 아예 안 온 경우. 클라이언트 오류이므로 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "입력값이 올바르지 않습니다.",
                        "fields", Map.of(e.getParameterName(), "필수 항목입니다")));
    }

    /**
     * @RequestParam / @PathVariable 에 붙인 제약조건(@NotBlank, @Min 등) 위반.
     * 본문 검증과 달리 ConstraintViolationException 으로 올라와 별도 처리가 필요하다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> fieldErrors = e.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> lastNode(violation.getPropertyPath().toString()),
                        ConstraintViolation::getMessage,
                        (first, second) -> first,
                        LinkedHashMap::new));

        return ResponseEntity.badRequest()
                .body(Map.of("error", "입력값이 올바르지 않습니다.", "fields", fieldErrors));
    }

    /** "search.q" 처럼 메서드명이 붙어 오므로 마지막 마디만 남긴다. */
    private String lastNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    /**
     * 요청 본문 자체를 읽지 못한 경우(JSON 문법 오류, enum 에 없는 값 등).
     * 클라이언트 입력 오류이므로 500 이 아니라 400 으로 돌려준다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "요청 본문을 해석할 수 없습니다. 필드 형식과 허용값을 확인해 주세요."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * DB 제약조건 위반.
     *
     * 업종·지역·규모는 코드 테이블을 참조하는 외래키라서, 목록에 없는 값을 보내면
     * 여기까지 올라온다. 그대로 두면 "서버 내부 오류"가 나가는데, 실제로는 보낸 값이
     * 틀린 것이라 사용자가 고칠 수 있다. 어떤 항목이 문제인지까지 알려준다.
     *
     * 다만 전부 400 으로 바꾸지는 않는다. NOT NULL 위반 같은 건 우리 코드가 값을
     * 빠뜨렸다는 뜻이라 사용자가 할 수 있는 일이 없다. 그런 건 500 으로 남겨
     * 로그에 스택이 찍히게 둔다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e) {
        String sqlState = e.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
        String message = e.getMostSpecificCause().getMessage();

        String reason = switch (sqlState == null ? "" : sqlState) {
            case FOREIGN_KEY_VIOLATION -> "선택할 수 없는 값입니다. 목록에 있는 값인지 확인해 주세요.";
            case UNIQUE_VIOLATION -> "이미 등록된 값입니다.";
            case CHECK_VIOLATION -> "허용 범위를 벗어난 값입니다.";
            default -> null;
        };
        if (reason == null) {
            return handleAsInternalError(e);
        }

        log.warn("입력값이 DB 제약조건에 걸렸습니다: {}", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", reason);
        offendingColumn(message).ifPresent(column -> body.put("fields", Map.of(column, reason)));
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> handleAsInternalError(Exception e) {
        log.error("처리하지 못한 오류", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "서버 내부 오류가 발생했습니다."));
    }

    /**
     * PostgreSQL 은 상세 메시지에 어떤 컬럼이 문제인지 적어 준다.
     * 예: Key (region)=(부산광역시) is not present in table "code_region".
     * 이걸 뽑아 주면 화면에서 해당 입력칸에 바로 표시할 수 있다.
     */
    private Optional<String> offendingColumn(String message) {
        if (message == null) {
            return Optional.empty();
        }
        Matcher matcher = OFFENDING_COLUMN.matcher(message);
        // 컬럼이 snake_case 라 JSON 필드명(camelCase)에 맞춰 돌려준다.
        return matcher.find() ? Optional.of(toCamelCase(matcher.group(1))) : Optional.empty();
    }

    private String toCamelCase(String columnName) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char c : columnName.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                result.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return result.toString();
    }

    /**
     * 예상 못 한 오류.
     *
     * 사용자에게는 내부 사정을 알리지 않되, <b>로그에는 스택까지 남긴다</b>.
     * 남기지 않으면 500 이 났을 때 원인을 찾을 방법이 없다(실제로 그래서 한참 헤맸다).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        return handleAsInternalError(e);
    }
}
