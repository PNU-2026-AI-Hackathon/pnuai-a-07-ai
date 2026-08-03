package com.safework.global.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "서버 내부 오류가 발생했습니다."));
    }
}
