package com.safework.ml.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.ml")
@Getter
@Setter
public class MlServerProperties {

    /** ML 서버(FastAPI) 주소 */
    private String baseUrl = "http://localhost:8000";

    /**
     * 임베딩 검색은 CPU 에서 수백 ms~수 초가 걸린다.
     * 다만 서버가 처음 뜬 직후에는 인덱스를 만드느라 훨씬 오래 걸리므로,
     * 그때는 타임아웃이 나고 폴백으로 넘어가는 것이 정상 동작이다.
     */
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(10);

    /** 끄면 ML 서버를 호출하지 않고 곧바로 폴백 경로를 쓴다. */
    private boolean enabled = true;
}
