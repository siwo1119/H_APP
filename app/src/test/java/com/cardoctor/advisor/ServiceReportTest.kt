package com.cardoctor.advisor

import com.cardoctor.advisor.data.report.ServiceReportBuilder
import com.cardoctor.advisor.domain.detect.AnomalyDetector
import com.cardoctor.advisor.domain.model.Daylight
import com.cardoctor.advisor.domain.model.DrivingContext
import com.cardoctor.advisor.domain.model.FluidId
import com.cardoctor.advisor.domain.model.FluidLevel
import com.cardoctor.advisor.domain.model.GearState
import com.cardoctor.advisor.domain.model.LightActualState
import com.cardoctor.advisor.domain.model.LightId
import com.cardoctor.advisor.domain.model.LightReading
import com.cardoctor.advisor.domain.model.LightSwitchState
import com.cardoctor.advisor.domain.model.VehicleSnapshot
import com.cardoctor.advisor.domain.model.Weather
import com.cardoctor.advisor.domain.severity.SeverityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정비소 사전 리포트(확장 아이디어 B) 검증.
 *
 * 정비소가 받는 JSON 이 **판단 근거까지 담고 있어야** 의미가 있다. 등급만 적혀 있으면
 * 정비소는 그 등급을 믿을 근거가 없고, 과잉 정비를 막는다는 목적도 달성되지 않는다.
 */
class ServiceReportTest {

    private val now = 1_700_000_000_000L

    private fun sampleSnapshot() = VehicleSnapshot(
        capturedAtMillis = now,
        lights = mapOf(
            LightId.LOW_BEAM to LightReading(LightSwitchState.ON, LightActualState.OFF),
        ),
        fluids = mapOf(FluidId.BRAKE_FLUID to FluidLevel.CRITICALLY_LOW),
        odometerKm = 48_250f,
        rangeRemainingKm = 310f,
        evStateOfChargePercent = 62f,
        gear = GearState.PARK,
    )

    private fun nightContext() = DrivingContext(
        daylight = Daylight.NIGHT,
        weather = Weather.RAIN,
        ambientTempC = 4f,
    )

    @Test
    fun `report carries evidence and weight breakdown for every finding`() {
        val snapshot = sampleSnapshot()
        val context = nightContext()
        val findings = SeverityEngine.evaluateAll(AnomalyDetector.detect(snapshot), context)

        val report = ServiceReportBuilder.build(findings, context, snapshot, now)

        assertEquals(findings.size, report.findings.size)
        report.findings.forEach { f ->
            assertTrue("판단 근거 신호가 비어 있으면 정비소가 검증할 수 없다", f.evidence.isNotBlank())
            assertTrue("감지 방식이 비어 있으면 안 된다", f.detectionPattern.isNotBlank())
            assertTrue("권고 문구가 비어 있으면 안 된다", f.recommendation.isNotBlank())
            // 긴급도 = 기본 심각도 + 가중치 합 이라는 관계가 리포트에서도 성립해야 한다.
            assertEquals(
                f.baseSeverity + f.appliedWeights.sumOf { it.points },
                f.urgencyScore,
            )
        }
    }

    @Test
    fun `report summary matches the worst finding`() {
        val snapshot = sampleSnapshot()
        val context = nightContext()
        val findings = SeverityEngine.evaluateAll(AnomalyDetector.detect(snapshot), context)
        val worst = findings.maxByOrNull { it.adviceLevel.level }!!

        val report = ServiceReportBuilder.build(findings, context, snapshot, now)

        assertEquals(findings.size, report.summary.totalFindings)
        assertEquals(worst.adviceLevel.level, report.summary.worstLevel)
        assertEquals(worst.adviceLevel.message, report.summary.recommendation)
    }

    @Test
    fun `simulated environment is flagged so the shop is not misled`() {
        val snapshot = sampleSnapshot()
        val demo = nightContext().copy(overridden = true)
        val findings = SeverityEngine.evaluateAll(AnomalyDetector.detect(snapshot), demo)

        val report = ServiceReportBuilder.build(findings, demo, snapshot, now)

        assertTrue("시연용 수동 환경이면 리포트에 명시돼야 한다", report.environment.simulated)
    }

    @Test
    fun `real environment is not flagged as simulated`() {
        val snapshot = sampleSnapshot()
        val context = nightContext()
        val findings = SeverityEngine.evaluateAll(AnomalyDetector.detect(snapshot), context)

        val report = ServiceReportBuilder.build(findings, context, snapshot, now)

        assertTrue(!report.environment.simulated)
    }

    @Test
    fun `report serialises to json with vehicle state included`() {
        val snapshot = sampleSnapshot()
        val context = nightContext()
        val findings = SeverityEngine.evaluateAll(AnomalyDetector.detect(snapshot), context)

        val json = ServiceReportBuilder.toJson(
            ServiceReportBuilder.build(findings, context, snapshot, now)
        )

        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.contains("\"odometerKm\""))
        assertTrue(json.contains("\"appliedWeights\""))
        assertTrue(json.contains("\"disclaimer\""))
        // 주행거리 같은 차량 상태가 실제로 실려야 정비소가 맥락을 알 수 있다.
        assertTrue(json.contains("48250"))
    }

    @Test
    fun `clean vehicle produces an empty but valid report`() {
        val snapshot = VehicleSnapshot(capturedAtMillis = now, odometerKm = 1_000f)
        val context = DrivingContext(daylight = Daylight.DAY, weather = Weather.CLEAR)
        val findings = SeverityEngine.evaluateAll(AnomalyDetector.detect(snapshot), context)

        val report = ServiceReportBuilder.build(findings, context, snapshot, now)

        assertEquals(0, report.summary.totalFindings)
        assertEquals(0, report.summary.worstLevel)
        assertEquals("이상 없음", report.summary.worstLevelName)
    }
}
