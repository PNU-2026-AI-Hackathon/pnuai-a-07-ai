package com.safework.llm;

import java.util.Optional;

/**
 * 답변 생성(LLM) 추상화.
 *
 * 검색(RAG 의 R)은 이미 되어 있고 여기가 생성(G) 자리다.
 * 어떤 모델을 쓸지는 아직 정해지지 않았고 바뀔 수도 있어서, 호출부가 특정 벤더에
 * 묶이지 않도록 인터페이스로 둔다.
 *
 * API 키가 없으면 available() 이 false 이고 generate() 는 empty 를 돌려준다.
 * 그 경우 서비스는 답변 없이 "관련 조문"만 반환한다 — 챗봇이 안 될 뿐 검색은 계속된다.
 */
public interface LlmClient {

    /** 지금 답변을 생성할 수 있는 상태인지 (키 설정 여부 등) */
    boolean available();

    /** 모델 이름. 대화 이력에 남긴다. */
    String modelName();

    Optional<LlmAnswer> generate(String systemPrompt, String userPrompt);

    /** 생성 결과. tokenUsage 는 제공하지 않는 모델도 있어 null 을 허용한다. */
    record LlmAnswer(String content, Integer tokenUsage, int latencyMs) {
    }
}
