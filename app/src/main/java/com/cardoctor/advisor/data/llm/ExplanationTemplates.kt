package com.cardoctor.advisor.data.llm

import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.domain.model.DrivingContext
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.domain.model.PartCategory

/**
 * 오프라인 설명 템플릿.
 *
 * LLM 호출이 실패하거나 API 키가 없을 때 쓰는 폴백이며, **기본 동작 경로이기도 하다.**
 * 시연 중 네트워크 사고는 흔하므로, 네트워크 없이도 권고 문구가 비지 않아야 한다.
 *
 * 등급·근거는 이미 [com.cardoctor.advisor.domain.severity.SeverityEngine] 이 확정했고,
 * 여기서는 문장만 만든다 — LLM 경로와 동일한 원칙이다.
 */
object ExplanationTemplates {

    fun summary(finding: Finding, context: DrivingContext): String {
        val part = finding.part.label
        return when (finding.adviceLevel) {
            AdviceLevel.PULL_OVER -> "$part 이상 — 안전한 곳에 정차하세요"
            AdviceLevel.IMMEDIATE_SERVICE -> "$part 이상 — 가까운 정비소로 가세요"
            AdviceLevel.AFTER_ARRIVAL -> "$part 점검 필요 — 목적지 도착 후 정비하세요"
            AdviceLevel.SCHEDULED -> "$part 점검 권장 — 주말에 정비하세요"
        }
    }

    fun detail(finding: Finding, context: DrivingContext): String {
        val cause = causeOf(finding)
        val risk = riskOf(finding, context)
        val action = finding.adviceLevel.message
        return listOf(cause, risk, action).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun causeOf(finding: Finding): String = when (finding.anomaly.category) {
        PartCategory.LIGHTING ->
            "${finding.part.label}에 점등 명령이 전달됐지만 실제로 켜지지 않았습니다. 전구 단선이나 배선·퓨즈 문제일 가능성이 높습니다."
        PartCategory.WIPER ->
            "와이퍼 작동 명령에 실제 동작이 따라오지 않았습니다. 모터나 링크 기구, 퓨즈 문제일 수 있습니다."
        PartCategory.TIRE ->
            "타이어 공기압이 기준을 벗어났습니다. 자연 감소, 못 등 이물질에 의한 펑크, 또는 온도 변화가 원인일 수 있습니다."
        PartCategory.BRAKING ->
            "제동 계통에서 이상 신호가 확인됐습니다. 브레이크액 부족이나 패드 마모, 센서 이상일 수 있습니다."
        PartCategory.STEERING ->
            "조향 계통에서 이상 신호가 확인됐습니다. 전동식 조향 장치나 관련 센서 문제일 수 있습니다."
        PartCategory.POWERTRAIN ->
            "엔진·냉각 계통에서 이상 신호가 확인됐습니다. 오일·냉각수 부족이나 과열이 원인일 수 있습니다."
        PartCategory.ENERGY ->
            "전원·배터리 계통에서 이상 신호가 확인됐습니다. 충전 상태나 배터리 노후가 원인일 수 있습니다."
        PartCategory.SAFETY ->
            "안전 장치에서 경고가 확인됐습니다."
        PartCategory.BODY ->
            "차체·도어 상태에서 이상이 확인됐습니다."
        PartCategory.CONSUMABLE ->
            "소모품 보충·교체 시점이 됐습니다."
    }

    private fun riskOf(finding: Finding, context: DrivingContext): String {
        // 등급을 올린 환경 요인을 그대로 문장에 반영한다 — "왜 이 등급인지"가 설명에서도 드러나야 한다.
        val weightReason = finding.appliedWeights.maxByOrNull { it.points }?.reason
        return when {
            weightReason != null && finding.anomaly.category == PartCategory.LIGHTING && context.isNight ->
                "야간에는 시야 확보가 어렵고 단속 대상입니다."
            weightReason != null && finding.anomaly.category == PartCategory.WIPER && context.isWet ->
                "${context.weather.label} 상황에서는 전방 시야 확보가 어렵습니다."
            weightReason != null && finding.anomaly.category == PartCategory.TIRE && context.isWet ->
                "젖은 노면에서는 접지력이 떨어져 제동거리가 길어집니다."
            weightReason != null && context.isHighway ->
                "고속 주행 중에는 작은 이상도 큰 사고로 이어질 수 있습니다."
            finding.floorApplied ->
                "안전과 직결된 부품이라 상황과 무관하게 조치가 필요합니다."
            else -> ""
        }
    }

    /** 이상이 하나도 없을 때의 종합 문구. */
    fun allClear(context: DrivingContext): String {
        val env = buildList {
            if (context.daylight.label != "—") add(context.daylight.label)
            if (context.weather.label != "—") add(context.weather.label)
        }.joinToString("·")
        return if (env.isBlank()) {
            "점검한 항목에서 이상이 발견되지 않았습니다."
        } else {
            "현재 $env 조건에서 점검한 항목 모두 정상입니다."
        }
    }
}
