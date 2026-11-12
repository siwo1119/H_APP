package com.cardoctor.advisor.domain.model

/**
 * 차량 부위 — 3D/다이어그램 하이라이트 단위이자 진단 결과의 귀속 지점.
 */
enum class CarPart(val label: String) {
    HEADLAMP_LEFT("좌측 전조등"),
    HEADLAMP_RIGHT("우측 전조등"),
    TAIL_LAMP("미등"),
    FOG_LAMP("안개등"),
    WIPER("와이퍼"),
    TIRE_FL("좌측 앞 타이어"),
    TIRE_FR("우측 앞 타이어"),
    TIRE_RL("좌측 뒤 타이어"),
    TIRE_RR("우측 뒤 타이어"),
    BRAKE("제동 장치"),
    ENGINE("엔진"),
    TRANSMISSION("변속기"),
    BATTERY("배터리"),
    STEERING("조향 장치"),
    AIRBAG("에어백"),
    DOOR("도어"),
    SEATBELT("안전벨트"),
    WASHER("워셔액"),
    BODY("차체"),
    ;

    val isTire: Boolean get() = this == TIRE_FL || this == TIRE_FR || this == TIRE_RL || this == TIRE_RR
}

/**
 * 부품 카테고리 — 환경 가중치는 개별 부품이 아니라 이 카테고리 단위로 적용된다.
 */
enum class PartCategory {
    LIGHTING,      // 등화류 — 야간 가중
    WIPER,         // 와이퍼 — 우천 가중
    TIRE,          // 타이어 — 우천/고속 가중
    BRAKING,       // 제동 — 우천/고속 가중
    STEERING,      // 조향 — 고속 가중
    POWERTRAIN,    // 엔진·냉각·변속 — 장거리 가중
    ENERGY,        // 배터리·연료 — 장거리 가중
    SAFETY,        // 에어백·안전벨트
    BODY,          // 도어·차체
    CONSUMABLE,    // 소모품
}

/**
 * 기본 심각도 1~3. 기획안 정의를 그대로 따른다.
 * CRITICAL(3) 은 안전 핵심 부품으로, 환경과 무관하게 최소 3등급으로 판정된다.
 */
enum class BaseSeverity(val score: Int) {
    LOW(1),
    MEDIUM(2),
    CRITICAL(3),
}

/**
 * 이상 감지 패턴 — 발표에서 "DTC 없이 추론한다"를 설명하는 축.
 */
enum class DetectionPattern(val label: String) {
    COMMAND_ACTUAL_MISMATCH("명령·실제 불일치"),
    THRESHOLD("임계값 초과"),
    DEVIATION("편차 과다"),
    TREND("추이 이상"),
    TELLTALE("경고등 점등"),
    COMBINATION("상태 조합"),
}

/**
 * 감지된 이상 1건. 등급 계산 전의 원시 소견이다.
 */
data class Anomaly(
    val id: String,
    val part: CarPart,
    val category: PartCategory,
    val title: String,
    /** 어떤 신호를 근거로 판단했는지 — 화면에 그대로 노출해 "설명 가능한 판단"을 뒷받침한다. */
    val evidence: String,
    val pattern: DetectionPattern,
    val baseSeverity: BaseSeverity,
    val detectedAtMillis: Long,
)

/**
 * 최종 정비 권고 4단계. 기획안 §심각도 × 주행환경 판단 로직.
 */
enum class AdviceLevel(
    val level: Int,
    val title: String,
    val message: String,
) {
    SCHEDULED(1, "여유 있게 정비", "주말이나 다음 정기점검 때 방문하세요"),
    AFTER_ARRIVAL(2, "목적지 후 정비", "목적지까지는 운행 가능합니다. 오늘 중 점검을 권장합니다"),
    IMMEDIATE_SERVICE(3, "즉시 정비", "가장 가까운 정비소로 바로 가세요"),
    PULL_OVER(4, "즉시 정차", "안전한 곳에 정차하고 견인·긴급출동을 부르세요"),
    ;

    companion object {
        /** 긴급도 점수 → 등급. 기획안 표 그대로. */
        fun fromUrgency(score: Int): AdviceLevel = when {
            score >= 6 -> PULL_OVER
            score >= 4 -> IMMEDIATE_SERVICE
            score >= 2 -> AFTER_ARRIVAL
            else -> SCHEDULED
        }
    }
}

/** 등급 산정에 반영된 환경 가중치 1건 — 화면에 근거로 표시한다. */
data class AppliedWeight(
    val reason: String,
    val points: Int,
)

/**
 * 등급까지 확정된 진단 1건.
 */
data class Finding(
    val anomaly: Anomaly,
    val urgencyScore: Int,
    val adviceLevel: AdviceLevel,
    val appliedWeights: List<AppliedWeight>,
    /** CRITICAL 부품이라 환경과 무관하게 최소 3등급으로 올라간 경우 true */
    val floorApplied: Boolean,
) {
    val part: CarPart get() = anomaly.part
    val title: String get() = anomaly.title
}
