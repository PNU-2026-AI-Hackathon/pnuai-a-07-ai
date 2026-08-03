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
     * 모델별로 프로젝트에 할당된 무료 쿼터가 다르다. 실제로 확인해 보니 신규 프로젝트에서
     * gemini-2.0-flash 계열은 "limit: 0" 으로 아예 호출이 안 되고(429),
     * gemini-flash-latest 는 정상 동작했다. 모델을 바꿀 때는 429 가 나지 않는지
     * 먼저 확인할 것.
     */
    private String model = "gemini-flash-latest";

    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
