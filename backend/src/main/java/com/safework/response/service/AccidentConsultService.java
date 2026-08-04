package com.safework.response.service;

import com.safework.law.dto.LawSearchResponse;
import com.safework.law.dto.LawSearchResponse.LawArticleDto;
import com.safework.law.service.LawSearchService;
import com.safework.llm.LlmClient;
import com.safework.response.dto.AccidentConsultDtos;
import com.safework.response.dto.AccidentConsultDtos.DutyDto;
import com.safework.response.dto.AccidentConsultDtos.GuidanceMode;
import com.safework.response.dto.AccidentConsultDtos.Request;
import com.safework.response.dto.AccidentConsultDtos.Response;
import com.safework.response.dto.AccidentConsultDtos.Section;
import com.safework.response.dto.AccidentConsultDtos.SeverityDto;
import com.safework.response.repository.AccidentAdviceRepository;
import com.safework.response.repository.AccidentResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사고 상황을 글로 받아 대처 방법을 안내한다.
 *
 * <p>재해유형을 고르게 하는 기존 대처 가이드({@link AccidentResponseService})와 달리,
 * 사고 직후에 있었던 일을 그대로 적으면 된다. 흐름은 이렇다.
 *
 * <pre>
 *   사고 서술
 *     → 재해유형·피해 정도 추정            (AccidentClassifier)
 *     → 근거 조문 모으기                    검색 + 조문번호로 직접 가져오기
 *     → 법정 의무·행정 절차·처벌 목록 구성  (StatutoryDutyCatalog)
 *     → 상황에 맞는 설명 생성               (LLM, 없으면 생략)
 * </pre>
 *
 * <p>조문 검색만으로는 부족하다. "지게차에 다리가 끼였다"는 서술에는 '보고', '조사표' 같은
 * 말이 없어서 산업재해 발생 보고 조문이 검색에 걸리지 않는다. 그래서 사고가 나면 무조건
 * 적용되는 조문은 검색에 맡기지 않고 조문번호로 직접 가져와 함께 근거로 쓴다.
 *
 * <p>LLM 이 없어도 응답은 비지 않는다. 목록은 조문에서 뽑아 둔 것이라 그대로 나가고,
 * 모델이 붙으면 설명(guidance)만 더 채워진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccidentConsultService {

    private static final int SEARCH_SIZE = 5;
    private static final int SIMILAR_CASE_LIMIT = 3;
    /** 한 조문에서 프롬프트에 담을 항(clause) 수. 제175조처럼 항이 많은 조문이 프롬프트를 잡아먹는다. */
    private static final int MAX_CLAUSES_PER_ARTICLE = 4;
    private static final int MAX_CITED_ARTICLES = 24;

    private static final int SUPPORT_PROGRAM_LIMIT = 5;

    private static final String NO_LLM_NOTE =
            "답변 생성 모델이 설정되지 않아 법령에서 정리한 의무 목록과 근거 조문만 보여드립니다.";
    private static final String NO_ANSWER_NOTE =
            "설명을 생성하지 못했습니다. 아래 의무 목록과 근거 조문을 확인해 주세요.";

    private static final String SEVERITY_NOTE_LIKELY =
            "적어 주신 내용에 중대재해로 이어질 수 있는 표현이 있어 중대재해 기준 안내를 함께 보여드립니다. "
                    + "중대재해에 해당하는지는 아래 기준으로 직접 확인하시고, 판단이 어려우면 "
                    + "관할 지방고용노동관서에 문의하세요.";
    private static final String SEVERITY_NOTE_UNKNOWN =
            "적어 주신 내용만으로는 중대재해 해당 여부를 판단할 수 없습니다. 아래 기준 중 하나라도 해당하면 "
                    + "중대재해이며, 즉시 작업을 중지하고 관할 지방고용노동관서에 지체 없이 보고해야 합니다.";

    private final AccidentClassifier classifier;
    private final StatutoryDutyCatalog dutyCatalog;
    private final ImmediateActionCatalog actionCatalog;
    private final AccidentConsultPromptBuilder promptBuilder;
    private final AccidentTypeVocabulary vocabulary;
    private final AccidentResponseRepository repository;
    private final AccidentAdviceRepository adviceRepository;
    private final LawSearchService lawSearchService;
    private final LlmClient llmClient;

    public Response consult(Request request) {
        String situation = request.getSituation() == null ? "" : request.getSituation().trim();
        if (situation.isEmpty()) {
            throw new IllegalArgumentException("어떤 사고가 났는지 입력해 주세요.");
        }

        AccidentClassifier.Result classified = classifier.classify(situation);
        // 사용자가 유형을 직접 골랐으면 추정보다 우선한다.
        String chosenType = trimToNull(request.getAccidentType());
        String accidentType = chosenType != null ? chosenType : classified.accidentType();
        boolean typeCertain = chosenType != null || classified.certain();

        boolean seriousLikely = isSeriousLikely(classified.severity());

        List<DutyDto> legalDuties = dutyCatalog.legalDuties(seriousLikely);
        // 행정 절차는 DB(admin_procedure)에서 가져온다. 서식 링크·담당 기관·과태료 금액이
        // 거기에만 있어서, 손으로 적어 둔 목록보다 사장님이 실제로 쓸 수 있는 정보가 많다.
        List<DutyDto> adminSteps = toDuties(adviceRepository.findProcedures(seriousLikely));
        List<DutyDto> penalties = dutyCatalog.penalties(seriousLikely);

        List<LawArticleDto> articles = collectArticles(situation, seriousLikely);
        String industry = trimToNull(request.getIndustry());
        List<AccidentResponseRepository.SimilarCase> cases = findSimilarCases(accidentType, industry);

        String industryKey = industry == null ? "" : industry;
        var precedents = adviceRepository.findPrecedents(industryKey, accidentType, seriousLikely);
        var programs = adviceRepository.findSupportPrograms(industryKey, SUPPORT_PROGRAM_LIMIT);

        Map<String, String> guidance = generate(situation, accidentType, classified.severity(),
                legalDuties, adminSteps, penalties, articles);

        GuidanceMode mode = guidance.isEmpty() ? GuidanceMode.RETRIEVAL_ONLY : GuidanceMode.GENERATED;
        String note = switch (mode) {
            case GENERATED -> null;
            case RETRIEVAL_ONLY -> llmClient.available() ? NO_ANSWER_NOTE : NO_LLM_NOTE;
        };

        return new Response(situation, accidentType, typeCertain, classifier.knownTypes(),
                severityOf(classified.severity(), seriousLikely),
                mode, note, mode == GuidanceMode.GENERATED ? llmClient.modelName() : null,
                actionCatalog.actions(),
                new Section(guidance.get(AccidentConsultPromptBuilder.LEGAL), legalDuties),
                new Section(guidance.get(AccidentConsultPromptBuilder.ADMINISTRATIVE), adminSteps),
                new Section(guidance.get(AccidentConsultPromptBuilder.PENALTY), penalties),
                toPrecedents(precedents), toSupportPrograms(programs),
                articles, cases, vocabulary.missingCaseReason(industry, cases.isEmpty()),
                ImmediateActionCatalog.DISCLAIMER);
    }

    private List<DutyDto> toDuties(List<AccidentAdviceRepository.Procedure> procedures) {
        return procedures.stream()
                .map(p -> new DutyDto(p.title(), p.actionSummary(), p.deadline(), p.legalBasis(),
                        // 기관이 '-' 로 들어 있는 행이 있다. 화면에 그대로 찍히지 않게 비운다.
                        blankIfDash(p.agency()), p.formName(), p.formUrl(), p.penalty()))
                .toList();
    }

    private List<AccidentConsultDtos.PrecedentDto> toPrecedents(
            List<AccidentAdviceRepository.Precedent> precedents) {
        return precedents.stream()
                .map(p -> new AccidentConsultDtos.PrecedentDto(p.caseName(), p.court(),
                        trimToNull(p.reference()), p.relevance(), p.summary(), p.url()))
                .toList();
    }

    private List<AccidentConsultDtos.SupportProgramDto> toSupportPrograms(
            List<AccidentAdviceRepository.Program> programs) {
        return programs.stream()
                .map(p -> new AccidentConsultDtos.SupportProgramDto(p.title(), p.agency(),
                        p.industryMatch() ? "이 업종을 대상으로 하는 지원사업입니다" : "사업주 지원사업",
                        summaryOf(p), trimToNull(p.deadline()), p.url()))
                .toList();
    }

    /** 지원 형태(융자·컨설팅)가 앞에 붙어야 사장님이 "돈인지 서비스인지"를 바로 안다. */
    private String summaryOf(AccidentAdviceRepository.Program program) {
        String type = program.supportType();
        String summary = program.summary() == null ? "" : program.summary();
        return type == null || type.isBlank() ? summary : type + " · " + summary;
    }

    private String blankIfDash(String value) {
        return value == null || "-".equals(value.trim()) ? null : value;
    }

    /**
     * 피해 정도를 알 수 없을 때도 중대재해 안내를 켠다.
     *
     * 사고 직후에는 부상 정도가 확정되지 않은 채로 적는 경우가 많다. 중대재해인데 안내를
     * 안 하는 쪽이 반대 경우보다 훨씬 위험해서, 확실히 경미할 때만 끈다.
     */
    private boolean isSeriousLikely(AccidentClassifier.Severity severity) {
        return severity != AccidentClassifier.Severity.MINOR;
    }

    private SeverityDto severityOf(AccidentClassifier.Severity severity, boolean seriousLikely) {
        String note = switch (severity) {
            case FATAL, SEVERE -> SEVERITY_NOTE_LIKELY;
            case UNKNOWN -> SEVERITY_NOTE_UNKNOWN;
            case MINOR -> SEVERITY_NOTE_UNKNOWN;
        };
        return new SeverityDto(severity.name(), seriousLikely, note,
                StatutoryDutyCatalog.SERIOUS_ACCIDENT_CRITERIA,
                StatutoryDutyCatalog.SERIOUS_ACCIDENT_CRITERIA_BASIS);
    }

    /**
     * 근거 조문 = 사고 서술로 찾은 조문(사고 자체의 예방 규정) + 번호로 집어 온 조문(보고·처벌).
     * 앞쪽에 사고 관련 조문을 두어, 프롬프트가 잘려도 이 사고에 고유한 내용이 살아남게 한다.
     */
    private List<LawArticleDto> collectArticles(String situation, boolean seriousLikely) {
        List<LawArticleDto> searched;
        try {
            searched = lawSearchService.search(situation, SEARCH_SIZE).getResults();
        } catch (IllegalArgumentException e) {
            // 검색어를 뽑지 못한 서술("다쳤어요")도 법정 의무 안내는 나가야 한다.
            searched = List.of();
        }

        List<String> keys = dutyCatalog.anchorArticles(seriousLikely).stream()
                .map(StatutoryDutyCatalog.ArticleRef::key)
                .toList();
        List<LawArticleDto> anchors = capClauses(repository.findArticlesByNo(keys));

        Set<String> seen = new LinkedHashSet<>();
        List<LawArticleDto> merged = new ArrayList<>();
        for (LawArticleDto article : searched) {
            if (seen.add(dedupeKey(article)) && merged.size() < MAX_CITED_ARTICLES) {
                merged.add(article);
            }
        }
        for (LawArticleDto article : anchors) {
            if (seen.add(dedupeKey(article)) && merged.size() < MAX_CITED_ARTICLES) {
                merged.add(article);
            }
        }
        return merged;
    }

    /** 항이 많은 조문(제175조는 7개)이 다른 조문을 밀어내지 않도록 조문당 개수를 제한한다. */
    private List<LawArticleDto> capClauses(List<AccidentResponseRepository.ArticleRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<LawArticleDto> capped = new ArrayList<>();
        for (AccidentResponseRepository.ArticleRow row : rows) {
            String key = row.lawName() + "|" + row.articleNo();
            int count = counts.merge(key, 1, Integer::sum);
            if (count <= MAX_CLAUSES_PER_ARTICLE) {
                capped.add(LawArticleDto.statute(row.articleId(), row.lawName(), row.articleNo(),
                        row.clauseNo(), row.title(), row.content()));
            }
        }
        return capped;
    }

    /** 검색 결과와 달리 여기서는 항까지 구분한다. 항마다 의무 내용이 달라 합치면 근거가 흐려진다. */
    private String dedupeKey(LawArticleDto article) {
        return article.getLawName() + "|" + article.getArticleNo() + "|" + article.getClauseNo();
    }

    private List<AccidentResponseRepository.SimilarCase> findSimilarCases(String accidentType,
                                                                         String industry) {
        return repository.findSimilarCases(vocabulary.toSifKinds(accidentType),
                vocabulary.toSifIndustry(industry), SIMILAR_CASE_LIMIT);
    }

    private Map<String, String> generate(String situation, String accidentType,
                                         AccidentClassifier.Severity severity,
                                         List<DutyDto> legalDuties, List<DutyDto> adminSteps,
                                         List<DutyDto> penalties,
                                         List<LawSearchResponse.LawArticleDto> articles) {
        var generated = llmClient.generate(promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(situation, accidentType, severity,
                        legalDuties, adminSteps, penalties, articles));
        if (generated.isEmpty()) {
            return Map.of();
        }
        // 제목을 하나도 못 찾으면 형식을 어긴 것이다. 버리지 말고 첫 덩어리에 통째로 넣어
        // 사장님이 읽을 수는 있게 한다.
        Map<String, String> parsed = promptBuilder.parse(generated.get().content());
        if (parsed.isEmpty()) {
            return Map.of(AccidentConsultPromptBuilder.LEGAL, generated.get().content().strip());
        }
        return parsed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
