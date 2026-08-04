package com.safework.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * LLM 호출용 HTTP 클라이언트.
 *
 * 타임아웃 설정은 클라이언트 코드가 아니라 여기서 한다. 그래야 테스트가 자기 몫의
 * RestClient(가짜 서버에 연결된 것)를 넣어 응답 파싱만 따로 확인할 수 있다.
 */
@Configuration
public class LlmClientConfig {

    public static final String GEMINI_REST_CLIENT = "geminiRestClient";

    @Bean(GEMINI_REST_CLIENT)
    public RestClient geminiRestClient(LlmProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
