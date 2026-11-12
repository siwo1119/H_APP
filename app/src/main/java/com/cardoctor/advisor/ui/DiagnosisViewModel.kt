package com.cardoctor.advisor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cardoctor.advisor.data.env.EnvironmentRepository
import com.cardoctor.advisor.data.llm.LlmExplainer
import com.cardoctor.advisor.data.local.HistoryStore
import com.cardoctor.advisor.data.vehicle.VehicleSignalRepository
import com.cardoctor.advisor.domain.detect.AnomalyDetector
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.domain.model.Daylight
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.domain.model.RoadType
import com.cardoctor.advisor.domain.model.Weather
import com.cardoctor.advisor.domain.severity.SeverityEngine
import com.cardoctor.advisor.data.report.ServiceReportExporter
import com.cardoctor.advisor.platform.AlertManager
import com.cardoctor.advisor.platform.DrivingStateMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 진단 흐름 전체를 조립한다.
 *
 * 렌더링은 2단이다 — 신호 수집 → 이상 감지 → 등급 판정 → 화면 반영까지는 **온디바이스에서 즉시**
 * 끝나고, LLM 설명만 뒤따라 채워진다. 네트워크가 없어도 진단·등급·그림은 그대로 동작한다.
 */
class DiagnosisViewModel(app: Application) : AndroidViewModel(app) {

    private val vehicleRepo = VehicleSignalRepository(app)
    private val envRepo = EnvironmentRepository()
    private val explainer = LlmExplainer()
    private val historyStore = HistoryStore(app)
    private val drivingMonitor = DrivingStateMonitor(app)
    private val alertManager = AlertManager(app)
    private val reportExporter = ServiceReportExporter(app)

    private val _state = MutableStateFlow(DiagnosisUiState())
    val state: StateFlow<DiagnosisUiState> = _state.asStateFlow()

    val history: StateFlow<List<HistoryStore.Record>> = historyStore.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var pollJob: Job? = null
    private var explainJob: Job? = null

    init {
        drivingMonitor.start()
        alertManager.start()
        viewModelScope.launch {
            drivingMonitor.state.collect { driving ->
                _state.value = _state.value.copy(driving = driving)
            }
        }
        viewModelScope.launch {
            envRepo.override.collect { _ -> refreshContextOnly() }
        }
        connect()
    }

    // ── 연결 / 폴링 ────────────────────────────────────────────────────────

    fun connect() {
        viewModelScope.launch {
            val ok = vehicleRepo.connect()
            _state.value = _state.value.copy(
                connected = ok,
                llmConfigured = explainer.isConfigured,
                connectionMessage = if (ok) {
                    null
                } else {
                    "Vehicle Service 에 연결하지 못했습니다. 에뮬레이터가 실행 중인지 확인하세요."
                },
            )
            if (ok) {
                runScan()
                startPolling()
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                runScan(showProgress = false)
            }
        }
    }

    /**
     * 전체 점검 1회.
     * @param showProgress 자가진단 실행 화면용 단계 진행률을 표시할지
     */
    fun runScan(showProgress: Boolean = true) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            if (showProgress) {
                _state.value = _state.value.copy(
                    scan = ScanProgress(phase = ScanPhase.SCANNING, steps = initialSteps()),
                )
            }

            val snapshot = vehicleRepo.readSnapshot(now)
            val context = envRepo.current(snapshot, now)
            val anomalies = AnomalyDetector.detect(snapshot)
            val findings = SeverityEngine.evaluateAll(anomalies, context)

            _state.value = _state.value.copy(
                snapshot = snapshot,
                context = context,
                findings = findings,
                lastScanAtMillis = now,
                scan = if (showProgress) {
                    ScanProgress(phase = ScanPhase.DONE, steps = completedSteps(findings))
                } else {
                    _state.value.scan
                },
            )

            historyStore.record(findings, now)
            raiseAlerts(findings)
            requestExplanations(findings)
        }
    }

    /**
     * 단계적 알림(확장 아이디어 A).
     * 설명이 이미 와 있으면 그 요약을, 아직이면 기본 권고 문구를 쓴다 —
     * 알림이 LLM 응답을 기다리느라 늦어지면 "즉시 정차" 안내의 의미가 없다.
     */
    private fun raiseAlerts(findings: List<Finding>) {
        val explanations = _state.value.explanations
        alertManager.notify(findings) { finding ->
            explanations[finding.anomaly.id]?.summary
        }
    }

    /** 환경 오버라이드가 바뀌면 신호를 다시 읽지 않고 등급만 재계산한다 (시연 중 즉각 반응). */
    private fun refreshContextOnly() {
        viewModelScope.launch {
            val current = _state.value
            if (current.findings.isEmpty() && current.snapshot.isEmpty) return@launch

            val now = System.currentTimeMillis()
            val context = envRepo.current(current.snapshot, now)
            val anomalies = AnomalyDetector.detect(current.snapshot)
            val findings = SeverityEngine.evaluateAll(anomalies, context)

            _state.value = _state.value.copy(context = context, findings = findings)
            raiseAlerts(findings)
            requestExplanations(findings)
        }
    }

    // ── 정비소 사전 리포트 (확장 아이디어 B) ───────────────────────────────

    /** 현재 진단 결과를 JSON 리포트 파일로 내보낸다. */
    fun exportReport() {
        viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(reportPath = null, reportExporting = true)
            val file = reportExporter.export(
                findings = s.findings,
                context = s.context,
                snapshot = s.snapshot,
                nowMillis = System.currentTimeMillis(),
            )
            _state.value = _state.value.copy(
                reportExporting = false,
                reportPath = file?.absolutePath,
            )
        }
    }

    /** 화면에서 미리보기로 보여줄 리포트 JSON. */
    fun reportPreview(): String {
        val s = _state.value
        return reportExporter.toJson(
            reportExporter.build(s.findings, s.context, s.snapshot, System.currentTimeMillis())
        )
    }

    // ── LLM 설명 (2단 렌더링의 뒷단) ────────────────────────────────────────

    private fun requestExplanations(findings: List<Finding>) {
        explainJob?.cancel()
        if (findings.isEmpty()) {
            _state.value = _state.value.copy(explanations = emptyMap())
            return
        }
        explainJob = viewModelScope.launch {
            val context = _state.value.context
            // 등급이 높은 것부터 — 사용자가 먼저 볼 항목의 설명이 먼저 채워지도록.
            findings.sortedByDescending { it.adviceLevel.level }.forEach { finding ->
                val explanation = explainer.explain(finding, context)
                val updated = _state.value.explanations + (finding.anomaly.id to explanation)
                _state.value = _state.value.copy(explanations = updated)
            }
        }
    }

    // ── 시연 오버라이드 ────────────────────────────────────────────────────

    fun overrideDaylight(value: Daylight?) =
        envRepo.setOverride { it.copy(daylight = value) }

    fun overrideWeather(value: Weather?) =
        envRepo.setOverride { it.copy(weather = value) }

    fun overrideRoadType(value: RoadType?) =
        envRepo.setOverride { it.copy(roadType = value) }

    fun clearOverrides() = envRepo.clearOverride()

    // ── 선택 ───────────────────────────────────────────────────────────────

    fun selectPart(part: CarPart?) {
        _state.value = _state.value.copy(selectedPart = part, selectedAnomalyId = null)
    }

    fun selectFinding(finding: Finding) {
        _state.value = _state.value.copy(
            selectedPart = finding.part,
            selectedAnomalyId = finding.anomaly.id,
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedPart = null, selectedAnomalyId = null)
    }

    fun clearHistory() {
        alertManager.reset()
        viewModelScope.launch { historyStore.clear() }
    }

    // ── 점검 단계 (UI 표시용) ───────────────────────────────────────────────

    private fun initialSteps(): List<ScanStep> = SCAN_STEP_LABELS.map { ScanStep(it) }

    private fun completedSteps(findings: List<Finding>): List<ScanStep> =
        SCAN_STEP_LABELS.mapIndexed { index, label ->
            val parts = SCAN_STEP_PARTS[index]
            ScanStep(
                label = label,
                done = true,
                findingCount = findings.count { it.part in parts },
            )
        }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        explainJob?.cancel()
        drivingMonitor.stop()
        alertManager.stop()
        vehicleRepo.disconnect()
        explainer.close()
        envRepo.close()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 4_000L

        val SCAN_STEP_LABELS = listOf(
            "등화 계통", "와이퍼", "타이어", "제동 계통",
            "엔진·냉각", "배터리·전원", "안전 장치", "도어·차체",
        )

        val SCAN_STEP_PARTS: List<Set<CarPart>> = listOf(
            setOf(CarPart.HEADLAMP_LEFT, CarPart.HEADLAMP_RIGHT, CarPart.TAIL_LAMP, CarPart.FOG_LAMP),
            setOf(CarPart.WIPER, CarPart.WASHER),
            setOf(CarPart.TIRE_FL, CarPart.TIRE_FR, CarPart.TIRE_RL, CarPart.TIRE_RR),
            setOf(CarPart.BRAKE),
            setOf(CarPart.ENGINE, CarPart.TRANSMISSION),
            setOf(CarPart.BATTERY),
            setOf(CarPart.AIRBAG, CarPart.SEATBELT, CarPart.STEERING),
            setOf(CarPart.DOOR, CarPart.BODY),
        )
    }
}
