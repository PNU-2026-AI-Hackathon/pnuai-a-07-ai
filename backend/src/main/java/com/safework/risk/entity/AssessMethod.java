package com.safework.risk.entity;

/**
 * risk_assessment.method 의 PostgreSQL enum(assess_method_t) 대응.
 * COLDSTART = DB 함수 기반(이력 없는 신규 사업장), HYBRID = 콜드스타트 + ML 결합.
 *
 * 주의: ML 모델을 XGBoost -> LightGBM 으로 바꾸기로 했으나 DB enum 은 아직
 * XGBOOST 라벨을 쓰고 있다. 값 이름은 DB 를 따른다(불일치 시 저장/조회가 깨짐).
 */
public enum AssessMethod {
    XGBOOST,
    COLDSTART,
    HYBRID
}
