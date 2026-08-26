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
     * 임베딩 검색은 CPU 에서 수백 ms 로 끝나지만 <b>위험유형 예측이 20초쯤 걸린다</b>.
     * SHAP 설명값을 붙이느라 요청마다 TreeExplainer 를 새로 만들기 때문이다(실측 19~21초).
     *
     * <p>10초로 두었더니 예측이 <b>매번</b> 타임아웃 나서 진단이 늘 COLDSTART 로만 남았다.
     * 우선 기다려서 받도록 올려 둔다. ML 쪽에서 explainer 를 캐싱하면 다시 낮출 수 있다.
     *
     * <p>서버가 처음 뜬 직후에는 인덱스를 만드느라 더 오래 걸리는데, 그때 타임아웃이 나고
     * 폴백으로 넘어가는 것은 정상 동작이다.
     */
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(30);

    /** 끄면 ML 서버를 호출하지 않고 곧바로 폴백 경로를 쓴다. */
    private boolean enabled = true;
}
