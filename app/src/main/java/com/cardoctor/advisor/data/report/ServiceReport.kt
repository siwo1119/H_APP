package com.cardoctor.advisor.data.report

import android.content.Context
import android.util.Log
import com.cardoctor.advisor.domain.model.DrivingContext
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.domain.model.VehicleSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 리포트 **내용**을 만드는 순수 로직. 안드로이드 의존이 없어 단위 테스트에서 그대로 검증한다.
 * 파일로 떨어뜨리는 일만 [ServiceReportExporter] 가 맡는다.
 */
object ServiceReportBuilder {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun build(
        findings: List<Finding>,
        context: DrivingContext,
        snapshot: VehicleSnapshot,
        nowMillis: Long,
    ): ServiceReportExporter.Report {
        val worst = findings.maxByOrNull { it.adviceLevel.level }
        return ServiceReportExporter.Report(
            generatedAt = TIMESTAMP_FORMAT.format(Date(nowMillis)),
            vehicle = ServiceReportExporter.VehicleInfo(
                odometerKm = snapshot.odometerKm,
                rangeRemainingKm = snapshot.rangeRemainingKm,
                evStateOfChargePercent = snapshot.evStateOfChargePercent,
                gear = snapshot.gear.name,
            ),
            environment = ServiceReportExporter.EnvironmentInfo(
                daylight = context.daylight.label,
                weather = context.weather.label,
                roadType = context.roadType.label,
                ambientTempC = context.ambientTempC,
                simulated = context.overridden,
            ),
            summary = ServiceReportExporter.Summary(
                totalFindings = findings.size,
                worstLevel = worst?.adviceLevel?.level ?: 0,
                worstLevelName = worst?.adviceLevel?.title ?: "이상 없음",
                recommendation = worst?.adviceLevel?.message
                    ?: "점검한 항목에서 이상이 발견되지 않았습니다",
            ),
            findings = findings.map { f ->
                ServiceReportExporter.ReportFinding(
                    part = f.part.label,
                    title = f.title,
                    evidence = f.anomaly.evidence,
                    detectionPattern = f.anomaly.pattern.label,
                    baseSeverity = f.anomaly.baseSeverity.score,
                    appliedWeights = f.appliedWeights.map {
                        ServiceReportExporter.WeightInfo(it.reason, it.points)
                    },
                    urgencyScore = f.urgencyScore,
                    adviceLevel = f.adviceLevel.level,
                    adviceLevelName = f.adviceLevel.title,
                    recommendation = f.adviceLevel.message,
                    safetyFloorApplied = f.floorApplied,
                )
            },
        )
    }

    fun toJson(report: ServiceReportExporter.Report): String =
        json.encodeToString(ServiceReportExporter.Report.serializer(), report)

    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
}

/**
 * 정비소 사전 리포트 — 확장 아이디어 B.
 *
 * 고장 부위·등급·판단 근거 신호를 한 벌로 묶어 정비소에 **미리** 전달할 수 있게 만든다.
 * 해외 사례 문서의 TribalScale 사례("심각도 판정 → 외부 연결"이 우승 공식)에서 가져온 것이고,
 * 운전자 입장에서는 견적을 미리 받아 **과잉 정비를 막는** 수단이다.
 *
 * ## 왜 JSON 파일인가
 * 지도·예약 API 연동은 키와 제휴가 필요해 범위 밖이다. 대신 **기계가 읽을 수 있는 형식**으로
 * 떨어뜨려 두면, 정비소 시스템 연동이든 카카오톡 전송이든 나중에 붙이기만 하면 된다.
 * 제언 문서도 "상태 스냅샷을 JSON 으로 내보내는 수준이면 충분"이라고 판정했다.
 */
class ServiceReportExporter(private val appContext: Context) {

    @Serializable
    data class Report(
        val schemaVersion: Int = SCHEMA_VERSION,
        val generatedAt: String,
        val app: String = "카닥터 AI",
        val vehicle: VehicleInfo,
        val environment: EnvironmentInfo,
        val summary: Summary,
        val findings: List<ReportFinding>,
        val disclaimer: String = DISCLAIMER,
    )

    @Serializable
    data class VehicleInfo(
        val odometerKm: Float? = null,
        val rangeRemainingKm: Float? = null,
        val evStateOfChargePercent: Float? = null,
        val gear: String,
    )

    @Serializable
    data class EnvironmentInfo(
        val daylight: String,
        val weather: String,
        val roadType: String,
        val ambientTempC: Float? = null,
        /** 실측값이 아니라 시연용 수동 설정이면 true — 정비소가 오해하지 않도록 명시한다. */
        val simulated: Boolean,
    )

    @Serializable
    data class Summary(
        val totalFindings: Int,
        val worstLevel: Int,
        val worstLevelName: String,
        val recommendation: String,
    )

    @Serializable
    data class ReportFinding(
        val part: String,
        val title: String,
        /** 어떤 신호를 근거로 판단했는지 — 정비소가 재현·검증할 수 있게 그대로 싣는다. */
        val evidence: String,
        val detectionPattern: String,
        val baseSeverity: Int,
        val appliedWeights: List<WeightInfo>,
        val urgencyScore: Int,
        val adviceLevel: Int,
        val adviceLevelName: String,
        val recommendation: String,
        val safetyFloorApplied: Boolean,
    )

    @Serializable
    data class WeightInfo(val reason: String, val points: Int)

    fun build(
        findings: List<Finding>,
        context: DrivingContext,
        snapshot: VehicleSnapshot,
        nowMillis: Long,
    ): Report = ServiceReportBuilder.build(findings, context, snapshot, nowMillis)

    fun toJson(report: Report): String = ServiceReportBuilder.toJson(report)

    /**
     * 리포트를 파일로 떨어뜨린다.
     * @return 저장된 파일. 실패하면 null.
     */
    suspend fun export(
        findings: List<Finding>,
        context: DrivingContext,
        snapshot: VehicleSnapshot,
        nowMillis: Long,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "reports")
            if (!dir.exists()) dir.mkdirs()
            val name = "cardoctor_report_${FILE_FORMAT.format(Date(nowMillis))}.json"
            val file = File(dir, name)
            file.writeText(toJson(build(findings, context, snapshot, nowMillis)))
            Log.i(TAG, "report written: ${file.absolutePath}")
            file
        }.getOrElse {
            Log.w(TAG, "report export failed: ${it.message}")
            null
        }
    }

    private companion object {
        const val TAG = "ServiceReport"
        const val SCHEMA_VERSION = 1
        const val DISCLAIMER =
            "등급은 규칙 기반으로 산정됐으며 참고용입니다. 최종 판단은 정비 전문가에게 받으십시오."
        val FILE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
    }
}
