package com.safework.report.service;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.safework.report.config.ReportProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HTML 문자열을 PDF 바이트로 변환한다.
 *
 * 한글이 깨지지 않으려면 TTF 를 임베드해야 하고, 굵은 글씨도 별도 파일로
 * 등록해야 한다(openhtmltopdf 는 굵기를 합성해 주지 않아 bold 파일이 없으면
 * 제목·표 헤더가 본문과 같은 굵기로 나온다).
 *
 * 폰트는 바이트로 미리 읽어 둔다. openhtmltopdf 가 렌더링마다 스트림을 요청하므로
 * 매번 파일을 열지 않도록 하기 위함이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfRenderer {

    private static final int WEIGHT_REGULAR = 400;
    private static final int WEIGHT_BOLD = 700;

    private final ReportProperties properties;

    private byte[] regularFont;
    private byte[] boldFont;

    @PostConstruct
    void loadFonts() {
        ReportProperties.Font font = properties.getFont();
        this.regularFont = load(font.getRegularPath(), font.getClasspathRegular(), "regular");
        this.boldFont = load(font.getBoldPath(), font.getClasspathBold(), "bold");
    }

    public byte[] render(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(out);

            String family = properties.getFont().getFamily();
            registerFont(builder, regularFont, family, WEIGHT_REGULAR);
            registerFont(builder, boldFont, family, WEIGHT_BOLD);

            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF 생성에 실패했습니다.", e);
        }
    }

    private void registerFont(PdfRendererBuilder builder, byte[] font, String family, int weight) {
        if (font == null) {
            return;
        }
        FSSupplier<InputStream> supplier = () -> new ByteArrayInputStream(font);
        builder.useFont(supplier, family, weight, FontStyle.NORMAL, true);
    }

    /** 파일 경로가 지정되어 있으면 그쪽을, 없으면 JAR 에 번들된 폰트를 읽는다. */
    private byte[] load(String filePath, String classpathLocation, String label) {
        if (filePath != null && !filePath.isBlank()) {
            Path path = Paths.get(filePath);
            if (Files.isRegularFile(path)) {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException e) {
                    throw new UncheckedIOException("폰트 파일을 읽을 수 없습니다: " + filePath, e);
                }
            }
            log.warn("지정한 {} 폰트 파일이 없어 번들 폰트를 사용합니다: {}", label, filePath);
        }

        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            // 조용히 넘어가면 한글이 공백으로 나오고 원인을 찾기 어려우므로 경고를 남긴다.
            log.warn("번들 {} 폰트를 찾을 수 없습니다: {}. PDF 의 한글이 깨질 수 있습니다.",
                    label, classpathLocation);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("번들 폰트를 읽을 수 없습니다: " + classpathLocation, e);
        }
    }
}
