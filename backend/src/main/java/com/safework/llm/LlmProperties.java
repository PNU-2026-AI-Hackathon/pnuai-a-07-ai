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

    /** 무료 티어에서 쓸 수 있는 모델 */
    private String model = "gemini-2.0-flash";

    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
