package com.cardoctor.advisor

import com.cardoctor.advisor.domain.detect.AnomalyDetector
import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.domain.model.Daylight
import com.cardoctor.advisor.domain.model.DetectionPattern
import com.cardoctor.advisor.domain.model.DrivingContext
import com.cardoctor.advisor.domain.model.FluidId
import com.cardoctor.advisor.domain.model.FluidLevel
import com.cardoctor.advisor.domain.model.LightActualState
import com.cardoctor.advisor.domain.model.LightId
import com.cardoctor.advisor.domain.model.LightReading
import com.cardoctor.advisor.domain.model.LightSwitchState
import com.cardoctor.advisor.domain.model.RoadType
import com.cardoctor.advisor.domain.model.TelltaleReading
import com.cardoctor.advisor.domain.model.TelltaleStateValue
import com.cardoctor.advisor.domain.model.TireReading
import com.cardoctor.advisor.domain.model.VehicleSnapshot
import com.cardoctor.advisor.domain.model.Weather
import com.cardoctor.advisor.domain.severity.SeverityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기획안 §사용자 시나리오 A·B·C 를 그대로 검증한다.
 *
 * 시연의 핵심 주장 — "같은 고장이라도 환경에 따라 판단이 달라진다" — 이 실제로 코드에서
 * 성립하는지 보증하는 테스트다. 가중치 표를 고치면 여기가 먼저 깨진다.
 */
class ScenarioTest {

    private val now = 1_700_000_000_000L

    private fun headlampOutSnapshot() = VehicleSnapshot(
        capturedAtMillis = now,
        lights = mapOf(
            LightId.LOW_BEAM to LightReading(
                switch = LightSwitchState.ON,
                actual = LightActualState.OFF,
            ),
        ),
    )

    // ── 시나리오 A — 낮, 전조등 고장 → 1등급 (주말 방문) ──────────────────

    @Test
    fun `scenario A - headlamp out during clear daytime is lowest level`() {
        val anomalies = AnomalyDetector.detect(headlampOutSnapshot())
        assertEquals(1, anomalies.size)
        assertEquals(DetectionPattern.COMMAND_ACTUAL_MISMATCH, anomalies[0].pattern)

        val context = DrivingContext(
            daylight = Daylight.DAY,
            weather = Weather.CLEAR,
            roadType = RoadType.CITY,
        )
        val finding = SeverityEngine.evaluate(anomalies[0], context)

        assertEquals(AdviceLevel.SCHEDULED, finding.adviceLevel)
        assertEquals(1, finding.urgencyScore)
        assertTrue("낮에는 환경 가중치가 없어야 한다", finding.appliedWeights.isEmpty())
    }

    // ── 시나리오 B — 밤, 같은 고장 → 3등급 (즉시 정비) ───────────────────

    @Test
    fun `scenario B - same headlamp fault at night escalates to immediate service`() {
        val anomalies = AnomalyDetector.detect(headlampOutSnapshot())
        val context = DrivingContext(
            daylight = Daylight.NIGHT,
            weather = Weather.CLEAR,
            roadType = RoadType.CITY,
        )
        val finding = SeverityEngine.evaluate(anomalies[0], context)

        // 기본 1 + 야간 3 = 4 → 3등급
        assertEquals(4, finding.urgencyScore)
        assertEquals(AdviceLevel.IMMEDIATE_SERVICE, finding.adviceLevel)
        assertEquals(1, finding.appliedWeights.size)
        assertEquals(3, finding.appliedWeights[0].points)
    }

    @Test
    fun `scenario A and B differ only by daylight`() {
        val anomaly = AnomalyDetector.detect(headlampOutSnapshot()).first()
        val day = SeverityEngine.evaluate(
            anomaly,
            DrivingContext(daylight = Daylight.DAY, weather = Weather.CLEAR),
        )
        val night = SeverityEngine.evaluate(
            anomaly,
            DrivingContext(daylight = Daylight.NIGHT, weather = Weather.CLEAR),
        )
        assertTrue(
            "같은 고장이 환경에 따라 다른 등급이 나와야 한다",
            night.adviceLevel.level > day.adviceLevel.level,
        )
    }

    // ── 시나리오 C — 비 오는 고속도로, 타이어 공기압 저하 → 3등급 ─────────

    @Test
    fun `scenario C - low tire pressure on wet highway is immediate service`() {
        val snapshot = VehicleSnapshot(
            capturedAtMillis = now,
            tires = mapOf(
                CarPart.TIRE_RR to TireReading(pressureKpa = 170f, lowThresholdKpa = 200f),
                CarPart.TIRE_FL to TireReading(pressureKpa = 230f, lowThresholdKpa = 200f),
                CarPart.TIRE_FR to TireReading(pressureKpa = 232f, lowThresholdKpa = 200f),
                CarPart.TIRE_RL to TireReading(pressureKpa = 228f, lowThresholdKpa = 200f),
            ),
        )
        val anomalies = AnomalyDetector.detect(snapshot)
        val low = anomalies.first { it.id == "tire-low-TIRE_RR" }
        assertEquals(DetectionPattern.THRESHOLD, low.pattern)

        val context = DrivingContext(
            daylight = Daylight.DAY,
            weather = Weather.RAIN,
            roadType = RoadType.HIGHWAY,
        )
        val finding = SeverityEngine.evaluate(low, context)

        // 기본 2 + 우천 2 + 고속 1 = 5 → 3등급
        assertEquals(5, finding.urgencyScore)
        assertEquals(AdviceLevel.IMMEDIATE_SERVICE, finding.adviceLevel)
    }

    // ── 안전 핵심 부품 하한 규칙 ──────────────────────────────────────────

    @Test
    fun `critical parts never fall below level 3 regardless of environment`() {
        val snapshot = VehicleSnapshot(
            capturedAtMillis = now,
            fluids = mapOf(FluidId.BRAKE_FLUID to FluidLevel.LOW),
        )
        val anomaly = AnomalyDetector.detect(snapshot).single()
        val finding = SeverityEngine.evaluate(
            anomaly,
            // 가중치가 하나도 안 붙는 가장 온화한 환경
            DrivingContext(daylight = Daylight.DAY, weather = Weather.CLEAR, roadType = RoadType.CITY),
        )

        // 점수만 보면 3점 → 2등급(목적지 후)이지만, 안전 핵심 부품이라 하한 규칙이 3등급으로 끌어올린다.
        assertEquals(3, finding.urgencyScore)
        assertEquals(AdviceLevel.AFTER_ARRIVAL, AdviceLevel.fromUrgency(finding.urgencyScore))
        assertEquals(AdviceLevel.IMMEDIATE_SERVICE, finding.adviceLevel)
        assertTrue("하한 규칙이 적용됐음이 기록돼야 한다", finding.floorApplied)
    }

    @Test
    fun `brake fluid critically low on wet highway reaches pull over`() {
        val snapshot = VehicleSnapshot(
            capturedAtMillis = now,
            fluids = mapOf(FluidId.BRAKE_FLUID to FluidLevel.CRITICALLY_LOW),
        )
        val anomaly = AnomalyDetector.detect(snapshot).single()
        val finding = SeverityEngine.evaluate(
            anomaly,
            DrivingContext(weather = Weather.RAIN, roadType = RoadType.HIGHWAY),
        )
        // 기본 3 + 우천 2 + 고속 1 = 6 → 4등급 즉시 정차
        assertEquals(6, finding.urgencyScore)
        assertEquals(AdviceLevel.PULL_OVER, finding.adviceLevel)
    }

    // ── 추론 패턴 ─────────────────────────────────────────────────────────

    @Test
    fun `light that is commanded off is not reported as a fault`() {
        val snapshot = VehicleSnapshot(
            capturedAtMillis = now,
            lights = mapOf(
                LightId.LOW_BEAM to LightReading(LightSwitchState.OFF, LightActualState.OFF),
            ),
        )
        assertTrue(AnomalyDetector.detect(snapshot).isEmpty())
    }

    @Test
    fun `unknown actual light state is treated as unsupported not as a fault`() {
        val snapshot = VehicleSnapshot(
            capturedAtMillis = now,
            lights = mapOf(
                LightId.LOW_BEAM to LightReading(LightSwitchState.ON, LightActualState.UNKNOWN),
            ),
        )
        assertTrue(
            "미지원 속성을 고장으로 보고하면 실차/에뮬 차이에서 오탐이 쏟아진다",
            AnomalyDetector.detect(snapshot).isEmpty(),
        )
    }

    @Test
    fun `tire pressure deviation is detected even when all wheels are above threshold`() {
        val snapshot = VehicleSnapshot(
            capturedAtMillis = now,
            tires = mapOf(
                CarPart.TIRE_FL to TireReading(260f, 200f),
                CarPart.TIRE_FR to TireReading(258f, 200f),
                CarPart.TIRE_RL to TireReading(255f, 200f),
                CarPart.TIRE_RR to TireReading(210f, 200f),
            ),
        )
        val anomalies = AnomalyDetector.detect(snapshot)
        assertEquals(1, anomalies.size)
        assertEquals(DetectionPattern.DEVIATION, anomalies[0].pattern)
        assertEquals(CarPart.TIRE_RR, anomalies[0].part)
    }

    @Test
    fun `blinking telltale is escalated above steady on`() {
        fun detectWith(state: TelltaleStateValue) = AnomalyDetector.detect(
            VehicleSnapshot(
                capturedAtMillis = now,
                telltales = listOf(TelltaleReading(type = "ABS", state = state, priority = 1)),
            )
        ).single()

        val steady = detectWith(TelltaleStateValue.ON)
        val blinking = detectWith(TelltaleStateValue.BLINK)
        assertTrue(blinking.baseSeverity.score > steady.baseSeverity.score)
    }

    @Test
    fun `informational telltales are not reported as faults`() {
        val snapshot = VehicleSnapshot(
            capturedAtMillis = now,
            telltales = listOf(
                TelltaleReading("TURN_SIGNAL_LEFT", TelltaleStateValue.BLINK, 1),
                TelltaleReading("HEADLAMP_HIGH", TelltaleStateValue.ON, 1),
                TelltaleReading("XEV_READY", TelltaleStateValue.ON, 1),
            ),
        )
        assertTrue(
            "방향지시등·하이빔 표시등은 고장이 아니다",
            AnomalyDetector.detect(snapshot).isEmpty(),
        )
    }

    @Test
    fun `findings are ordered by advice level descending`() {
        val snapshot = VehicleSnapshot(
            capturedAtMillis = now,
            lights = mapOf(
                LightId.LOW_BEAM to LightReading(LightSwitchState.ON, LightActualState.OFF),
            ),
            fluids = mapOf(FluidId.BRAKE_FLUID to FluidLevel.CRITICALLY_LOW),
        )
        val findings = SeverityEngine.evaluateAll(
            AnomalyDetector.detect(snapshot),
            DrivingContext(daylight = Daylight.DAY, weather = Weather.CLEAR),
        )
        assertEquals(2, findings.size)
        assertEquals(CarPart.BRAKE, findings[0].part)
        assertTrue(findings[0].adviceLevel.level >= findings[1].adviceLevel.level)
    }
}
