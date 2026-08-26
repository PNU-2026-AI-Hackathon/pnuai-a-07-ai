package com.safework.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.llm")
@Getter
@Setter
public class LlmProperties {

    /**
     * API 키. 코드나 설정 파일에 적지 말고 환경변수(GEMINI_API_KEY)로 넣는다.
     * 비어 있으면 답변 생성이 꺼지고 검색 결과만 반환한다.
     */
    private String apiKey;

    private String baseUrl = "https://generativelanguage.googleapis.com";

    /**
     * 무료 티어에서 쓸 수 있는 모델.
     *
     * <p>모델마다 <b>하루</b> 무료 호출 수가 다르다. 2026-08-04 에 실제로 눌러 확인한 값이다.
     *
     * <pre>
     *   gemini-flash-latest       429  하루 20건   (지금은 gemini-3.6-flash 를 가리킴)
     *   gemini-2.0-flash          429  하루  0건   (배정 자체가 없음)
     *   gemini-2.5-flash          404
     *   gemini-flash-lite-latest  200              ← 채택 (gemini-3.5-flash-lite)
     * </pre>
     *
     * <p>{@code -latest} 는 별칭이라 가리키는 모델이 바뀐다. 어느 날 갑자기 429 가 나기
     * 시작하면 쿼터가 아니라 <b>별칭이 옮겨간 것</b>일 수 있으니, 로그의 실제 모델명을
     * 확인할 것. 답변 품질은 조문 요약·인용 수준에서 충분한 것을 확인했다.
     */
    private String model = "gemini-flash-lite-latest";

    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
