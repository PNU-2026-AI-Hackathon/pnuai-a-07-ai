package com.safework.report.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.report")
@Getter
@Setter
public class ReportProperties {

    /** 생성한 PDF 를 저장할 디렉터리 */
    private String storageDir = "reports";

    /**
     * PDF 에 임베드할 한글 폰트. 한글은 PDF 내장 폰트로 렌더링되지 않으므로
     * TTF 임베드가 필수다.
     *
     * 기본값은 JAR 에 함께 담기는 나눔고딕(OFL 1.1)이라 Windows·Linux·Docker
     * 어디서든 동일하게 동작한다. 시스템 폰트를 쓰고 싶으면 regular-path /
     * bold-path 로 파일 경로를 지정하면 그쪽이 우선한다.
     */
    private Font font = new Font();

    @Getter
    @Setter
    public static class Font {

        /** PDF 안에서 사용할 폰트 패밀리명 (템플릿 CSS 의 font-family 와 일치해야 함) */
        private String family = "ReportKorean";

        /** JAR 에 번들된 폰트 (클래스패스 기준) */
        private String classpathRegular = "fonts/NanumGothic-Regular.ttf";
        private String classpathBold = "fonts/NanumGothic-Bold.ttf";

        /** 지정 시 번들 폰트 대신 이 파일을 사용 (선택) */
        private String regularPath;
        private String boldPath;
    }
}
