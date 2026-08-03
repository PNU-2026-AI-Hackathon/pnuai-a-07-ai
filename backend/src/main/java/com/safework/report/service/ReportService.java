package com.safework.report.service;

import com.safework.prevention.dto.PreventionGuideRequest;
import com.safework.prevention.service.PreventionGuideService;
import com.safework.report.config.ReportProperties;
import com.safework.report.dto.DeficientItemView;
import com.safework.report.dto.PredictionView;
import com.safework.report.dto.ReportCreateResponse;
import com.safework.report.entity.Report;
import com.safework.report.entity.ReportStatus;
import com.safework.report.repository.DiagnosisLawBasisRepository;
import com.safework.report.repository.ReportRepository;
import com.safework.risk.entity.RiskAssessment;
import com.safework.risk.entity.RiskGrade;
import com.safework.risk.repository.RiskAssessmentRepository;
import com.safework.workplace.entity.Workplace;
import com.safework.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final int REPORT_TOP_ACCIDENTS = 3;
    private static final int REPORT_ITEMS_PER_ACCIDENT = 5;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WorkplaceRepository workplaceRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final DiagnosisLawBasisRepository diagnosisLawBasisRepository;
    private final PreventionGuideService preventionGuideService;
    private final ReportRepository reportRepository;
    private final TemplateEngine templateEngine;
    private final PdfRenderer pdfRenderer;
    private final ReportProperties properties;

    @Transactional
    public ReportCreateResponse create(Long memberId, Long workplaceId) {
        Workplace workplace = workplaceRepository.findByIdAndOwnerId(workplaceId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));

        RiskAssessment assessment = riskAssessmentRepository
                .findFirstByWorkplaceIdOrderByAssessedAtDesc(workplaceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "위험도 진단 결과가 없습니다. 체크리스트를 먼저 제출해 주세요."));

        Report report = reportRepository.save(Report.builder()
                .assessmentId(assessment.getId())
                .workplaceId(workplaceId)
                .status(ReportStatus.GENERATING)
                .build());

        try {
            byte[] pdf = pdfRenderer.render(renderHtml(workplace, assessment));
            Path path = writeFile(report.getId(), pdf);
            report.markDone(path.toString(), pdf.length);
        } catch (RuntimeException e) {
            report.markFailed();
            throw e;
        }

        return new ReportCreateResponse(report.getId(), report.getStatus().name(),
                report.getFileSize(), report.getGeneratedAt());
    }

    public Report getForDownload(Long memberId, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("리포트를 찾을 수 없습니다."));

        // 리포트 자체에는 소유자 정보가 없으므로 사업장으로 권한을 확인한다.
        workplaceRepository.findByIdAndOwnerId(report.getWorkplaceId(), memberId)
                .orElseThrow(() -> new IllegalArgumentException("리포트를 찾을 수 없습니다."));

        if (report.getStatus() != ReportStatus.DONE || report.getFilePath() == null) {
            throw new IllegalArgumentException("아직 내려받을 수 있는 리포트가 아닙니다.");
        }
        return report;
    }

    public byte[] readFile(Report report) {
        try {
            return Files.readAllBytes(Paths.get(report.getFilePath()));
        } catch (IOException e) {
            throw new UncheckedIOException("리포트 파일을 읽을 수 없습니다.", e);
        }
    }

    private String renderHtml(Workplace workplace, RiskAssessment assessment) {
        Context context = new Context();
        context.setVariable("fontFamily", properties.getFont().getFamily());

        context.setVariable("workplaceName", workplace.getName());
        context.setVariable("industry", workplace.getIndustry());
        context.setVariable("subIndustry", workplace.getSubIndustry());
        context.setVariable("sizeClass", workplace.getSizeClass());
        context.setVariable("region", workplace.getRegion());
        context.setVariable("employeeCount", workplace.getEmployeeCount());
        context.setVariable("address", workplace.getAddress());

        context.setVariable("riskScore", assessment.getRiskScore().stripTrailingZeros().toPlainString());
        context.setVariable("riskGrade", assessment.getRiskGrade().name());
        context.setVariable("riskGradeLabel", gradeLabel(assessment.getRiskGrade()));
        context.setVariable("topAccidentType", assessment.getTopAccidentType());
        context.setVariable("methodLabel", methodLabel(assessment));
        context.setVariable("assessedAt", TIMESTAMP.format(assessment.getAssessedAt()));
        context.setVariable("generatedAt", TIMESTAMP.format(java.time.OffsetDateTime.now()));

        Map<String, Object> raw = assessment.getRawFeatures();
        context.setVariable("baseComponent", raw == null ? null : raw.get("base"));
        context.setVariable("checklistComponent", raw == null ? null : raw.get("checklist"));

        context.setVariable("deficientItems", loadDeficientItems(workplace.getId()));
        context.setVariable("predictions", loadPredictions(workplace));

        return templateEngine.process("report/safety-report", context);
    }

    /** 평평하게 나오는 (항목 × 법령) 행을 항목 기준으로 묶는다. */
    private List<DeficientItemView> loadDeficientItems(Long workplaceId) {
        Map<String, DeficientItemView> byCode = new LinkedHashMap<>();
        Map<String, List<DeficientItemView.LawView>> lawsByCode = new LinkedHashMap<>();

        for (var row : diagnosisLawBasisRepository.findByWorkplace(workplaceId)) {
            lawsByCode.computeIfAbsent(row.itemCode(), k -> new java.util.ArrayList<>())
                    .add(new DeficientItemView.LawView(row.lawName(), row.articleNo(), row.title()));

            byCode.computeIfAbsent(row.itemCode(), k -> new DeficientItemView(
                    row.itemCode(), row.category(), row.workType(), row.question(),
                    row.riskWeight(), row.critical(), lawsByCode.get(row.itemCode())));
        }
        return List.copyOf(byCode.values());
    }

    private List<PredictionView> loadPredictions(Workplace workplace) {
        PreventionGuideRequest request = new PreventionGuideRequest();
        request.setIndustry(workplace.getIndustry());
        request.setSizeClass(workplace.getSizeClass());
        request.setRegion(workplace.getRegion());
        request.setExpectedAccidentCount(REPORT_TOP_ACCIDENTS);
        request.setItemsPerAccident(REPORT_ITEMS_PER_ACCIDENT);

        return preventionGuideService.getGuide(request).getPredictions().stream()
                .map(PredictionView::new)
                .toList();
    }

    private Path writeFile(Long reportId, byte[] pdf) {
        try {
            Path dir = Paths.get(properties.getStorageDir()).toAbsolutePath();
            Files.createDirectories(dir);
            Path path = dir.resolve("safework-report-" + reportId + ".pdf");
            Files.write(path, pdf);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("리포트 파일을 저장할 수 없습니다.", e);
        }
    }

    private String gradeLabel(RiskGrade grade) {
        return switch (grade) {
            case CRITICAL -> "매우 위험";
            case HIGH -> "위험";
            case MEDIUM -> "보통";
            case LOW -> "양호";
        };
    }

    private String methodLabel(RiskAssessment assessment) {
        String version = Objects.toString(assessment.getModelVersion(), "");
        return switch (assessment.getMethod()) {
            case COLDSTART -> "통계 기반 진단 (" + version + ")";
            case XGBOOST -> "머신러닝 예측 (" + version + ")";
            case HYBRID -> "통계 + 머신러닝 (" + version + ")";
        };
    }
}
