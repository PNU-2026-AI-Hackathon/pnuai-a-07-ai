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
     * PDF 에 임베드할 한글 폰트 경로.
     * 한글은 내장 폰트로 렌더링되지 않으므로 TTF 를 반드시 임베드해야 한다.
     * 로컬(Windows)은 맑은 고딕을 기본값으로 쓰지만, 배포 이미지에는 재배포 가능한
     * 폰트(예: 나눔고딕 OFL)를 넣고 이 값을 바꿔야 한다.
     */
    private String fontPath = "C:/Windows/Fonts/malgun.ttf";

    /** PDF 안에서 사용할 폰트 패밀리명 (템플릿 CSS 의 font-family 와 일치해야 함) */
    private String fontFamily = "ReportKorean";
}
