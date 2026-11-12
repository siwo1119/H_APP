package com.cardoctor.advisor.domain.severity

import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.domain.model.Anomaly
import com.cardoctor.advisor.domain.model.AppliedWeight
import com.cardoctor.advisor.domain.model.BaseSeverity
import com.cardoctor.advisor.domain.model.DrivingContext
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.domain.model.PartCategory
import com.cardoctor.advisor.domain.model.Weather

/**
 * 규칙 기반 심각도 엔진. **등급은 전적으로 여기서 결정되고, LLM 은 관여하지 않는다.**
 *
 *   긴급도 = 기본 심각도(1~3) + Σ 환경 가중치(0~3)
 *   단, 기본 심각도 CRITICAL(3) 은 환경과 무관하게 최소 3등급으로 판정한다.
 *
 * 순수 함수로 두어 단위 테스트에서 시나리오 A/B/C 를 그대로 검증할 수 있게 한다.
 */
object SeverityEngine {

    fun evaluate(anomaly: Anomaly, context: DrivingContext): Finding {
        val weights = weightsFor(anomaly.category, context)
        val rawScore = anomaly.baseSeverity.score + weights.sumOf { it.points }

        val byScore = AdviceLevel.fromUrgency(rawScore)

        // 안전 핵심 부품 하한 규칙
        val isCritical = anomaly.baseSeverity == BaseSeverity.CRITICAL
        val floored = isCritical && byScore.level < AdviceLevel.IMMEDIATE_SERVICE.level
        val finalLevel = if (floored) AdviceLevel.IMMEDIATE_SERVICE else byScore

        return Finding(
            anomaly = anomaly,
            urgencyScore = rawScore,
            adviceLevel = finalLevel,
            appliedWeights = weights,
            floorApplied = floored,
        )
    }

    fun evaluateAll(anomalies: List<Anomaly>, context: DrivingContext): List<Finding> =
        anomalies.map { evaluate(it, context) }
            .sortedWith(compareByDescending<Finding> { it.adviceLevel.level }.thenByDescending { it.urgencyScore })

    /**
     * 환경 가중치표. 기획안 "환경 가중치 예시" 를 카테고리 단위로 옮긴 것.
     * 값을 바꾸려면 여기만 고치면 되고, 바뀐 근거는 화면에 그대로 표시된다.
     */
    private fun weightsFor(category: PartCategory, ctx: DrivingContext): List<AppliedWeight> {
        val out = mutableListOf<AppliedWeight>()

        if (ctx.isNight && category == PartCategory.LIGHTING) {
            out += AppliedWeight("야간 운행 — 시야 확보 필요", 3)
        }
        if (ctx.isWet) {
            when (category) {
                PartCategory.WIPER -> out += AppliedWeight("${ctx.weather.label} — 시야 확보 필요", 3)
                PartCategory.TIRE, PartCategory.BRAKING ->
                    out += AppliedWeight("${ctx.weather.label} — 노면 마찰 저하", 2)
                else -> Unit
            }
        }
        if (ctx.weather == Weather.FOG && category == PartCategory.LIGHTING) {
            out += AppliedWeight("안개 — 시야 확보 필요", 2)
        }
        if (ctx.isHighway) {
            when (category) {
                PartCategory.TIRE, PartCategory.BRAKING, PartCategory.STEERING ->
                    out += AppliedWeight("고속도로 주행 — 고속 주행 위험", 1)
                else -> Unit
            }
        }
        if (ctx.longTrip) {
            when (category) {
                PartCategory.POWERTRAIN, PartCategory.ENERGY ->
                    out += AppliedWeight("장거리 운행 — 부하 누적", 1)
                else -> Unit
            }
        }
        if (ctx.isExtremeTemp && category == PartCategory.POWERTRAIN) {
            out += AppliedWeight("혹서·혹한 — 열부하 증가", 1)
        }
        return out
    }

}
