package com.cardoctor.advisor.ui

import com.cardoctor.advisor.data.llm.LlmExplainer
import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.domain.model.DrivingContext
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.domain.model.VehicleSnapshot
import com.cardoctor.advisor.platform.DrivingStateMonitor

/** 차량 다이어그램의 부위별 표시 색. 기획안의 정상 회색 / 주의 노랑 / 위험 빨강. */
enum class PartStatus {
    NORMAL, CAUTION, DANGER
}

enum class ScanPhase {
    IDLE, SCANNING, DONE
}

data class ScanProgress(
    val phase: ScanPhase = ScanPhase.IDLE,
    /** 점검 항목별 진행 상태 — 자가진단 실행 화면에서 보여준다. */
    val steps: List<ScanStep> = emptyList(),
) {
    val completed: Int get() = steps.count { it.done }
    val total: Int get() = steps.size
    val ratio: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

data class ScanStep(
    val label: String,
    val done: Boolean = false,
    val findingCount: Int = 0,
)

data class DiagnosisUiState(
    val connected: Boolean = false,
    val connectionMessage: String? = null,
    val snapshot: VehicleSnapshot = VehicleSnapshot(),
    val context: DrivingContext = DrivingContext(),
    val findings: List<Finding> = emptyList(),
    /** anomaly id → 설명. LLM 응답이 늦게 도착하므로 등급·그림과 분리해 채운다. */
    val explanations: Map<String, LlmExplainer.Explanation> = emptyMap(),
    val driving: DrivingStateMonitor.State = DrivingStateMonitor.State(),
    val scan: ScanProgress = ScanProgress(),
    val lastScanAtMillis: Long = 0L,
    val llmConfigured: Boolean = false,
    val selectedPart: CarPart? = null,
    val selectedAnomalyId: String? = null,
) {
    /** 가장 높은 등급 — 상단 종합 상태 배지에 쓴다. */
    val worstLevel: AdviceLevel? get() = findings.maxByOrNull { it.adviceLevel.level }?.adviceLevel

    val hasCriticalFinding: Boolean
        get() = findings.any { it.adviceLevel.level >= AdviceLevel.IMMEDIATE_SERVICE.level }

    fun statusOf(part: CarPart): PartStatus {
        val level = findings.filter { it.part == part }.maxByOrNull { it.adviceLevel.level }
            ?.adviceLevel?.level ?: return PartStatus.NORMAL
        return if (level >= AdviceLevel.IMMEDIATE_SERVICE.level) PartStatus.DANGER else PartStatus.CAUTION
    }

    fun findingsFor(part: CarPart): List<Finding> = findings.filter { it.part == part }

    val selectedFinding: Finding?
        get() = selectedAnomalyId?.let { id -> findings.firstOrNull { it.anomaly.id == id } }
            ?: selectedPart?.let { p -> findingsFor(p).firstOrNull() }

    fun explanationFor(finding: Finding): LlmExplainer.Explanation? = explanations[finding.anomaly.id]
}
