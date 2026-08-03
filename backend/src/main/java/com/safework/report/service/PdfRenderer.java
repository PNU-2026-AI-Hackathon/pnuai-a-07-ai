package com.safework.report.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.safework.report.config.ReportProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * HTML 문자열을 PDF 바이트로 변환한다.
 * 한글이 깨지지 않도록 TTF 폰트를 임베드하는 것이 핵심.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfRenderer {

    private final ReportProperties properties;

    private File fontFile;

    @PostConstruct
    void resolveFont() {
        File file = new File(properties.getFontPath());
        if (file.isFile()) {
            this.fontFile = file;
        } else {
            // 폰트가 없으면 PDF 는 만들어지되 한글이 공백으로 나온다. 조용히 넘어가면
            // 원인을 찾기 어려우므로 경고를 남긴다.
            log.warn("한글 폰트를 찾을 수 없습니다: {}. PDF 의 한글이 깨질 수 있습니다. "
                    + "app.report.font-path 를 확인하세요.", properties.getFontPath());
        }
    }

    public byte[] render(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(out);

            if (fontFile != null) {
                builder.useFont(fontFile, properties.getFontFamily());
            }

            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 생성에 실패했습니다.", e);
        }
    }
}
