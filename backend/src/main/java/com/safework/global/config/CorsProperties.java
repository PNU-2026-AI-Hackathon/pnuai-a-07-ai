package com.safework.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 브라우저가 다른 주소의 백엔드를 부를 수 있게 허용할 출처.
 *
 * <p>개발 중에는 Vite 가 <code>/api</code> 를 프록시해 줘서 브라우저 입장에서는 같은
 * 주소라 CORS 가 필요 없다. 하지만 프론트를 GitHub Pages 같은 곳에 올리면 주소가
 * 달라져서, 이 설정이 없으면 <b>브라우저가 모든 요청을 막는다</b>(로그인조차 안 된다).
 *
 * <p>출처는 환경변수로 넣는다. 배포 주소가 정해지기 전에도 코드를 고치지 않도록.
 * <pre>
 *   APP_CORS_ALLOWED_ORIGINS=https://our-frontend.example.com,https://another.example.com
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * 허용할 프론트엔드 출처. 기본값은 로컬 개발 서버와 팀 GitHub Pages 주소다.
     *
     * <p>패턴을 쓰므로 <code>https://*.trycloudflare.com</code> 처럼 와일드카드도 된다
     * (시연용 터널 주소가 켤 때마다 바뀌기 때문).
     */
    private List<String> allowedOrigins = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "https://pnu-2026-ai-hackathon.github.io",
            "https://*.trycloudflare.com");

    /** 브라우저가 preflight 결과를 재사용할 시간(초). 매 요청마다 OPTIONS 를 보내지 않게 한다. */
    private long maxAge = 3600;
}
