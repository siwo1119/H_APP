package com.cardoctor.advisor.domain.detect

import com.cardoctor.advisor.domain.model.Anomaly
import com.cardoctor.advisor.domain.model.BaseSeverity
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.domain.model.DetectionPattern
import com.cardoctor.advisor.domain.model.FluidLevel
import com.cardoctor.advisor.domain.model.GearState
import com.cardoctor.advisor.domain.model.LightActualState
import com.cardoctor.advisor.domain.model.LightSwitchState
import com.cardoctor.advisor.domain.model.PartCategory
import com.cardoctor.advisor.domain.model.TelltaleStateValue
import com.cardoctor.advisor.domain.model.VehicleSnapshot
import kotlin.math.abs

/**
 * 상태 신호에서 이상을 추론한다.
 *
 * Pleos Vehicle SDK 2.3.2 에는 DTC(고장코드) API 가 없다. 21개 도메인이 전부 상태 조회·제어·구독이다.
 * 따라서 "고장코드를 읽어 번역"하는 대신 아래 패턴으로 이상을 **추론**한다.
 *
 *   1. 명령 − 실제 불일치 — 전조등을 켰는데 점등 상태가 OFF
 *                          (Light/Wiper 는 switch 와 state 가 별도 API 로 제공된다)
 *   2. 임계값 / 편차      — 타이어 공기압이 기준 미만이거나 4륜 간 편차 과다
 *   3. 추이               — 누적 주행거리 기반 소모품 주기 도래
 *   4. 경고등             — Safety.getTelltalesInfo() 로 직접 읽는다
 *   5. 상태 조합          — 도어 열림 + 주행 중
 *
 * 순수 함수. 입력은 정규화된 스냅샷뿐이고 SDK 타입에 의존하지 않는다.
 */
object AnomalyDetector {

    /** SDK 가 저압 임계값을 주지 않을 때 쓰는 기본값 (kPa). 승용 기준 약 2.0 bar. */
    const val DEFAULT_LOW_PRESSURE_KPA = 200f

    /** 4륜 간 공기압 편차 허용 비율. 이를 넘으면 편차 이상으로 본다. */
    const val PRESSURE_DEVIATION_RATIO = 0.15f

    /** 소모품 점검 주기 (km). */
    const val SERVICE_INTERVAL_KM = 10_000f

    fun detect(snapshot: VehicleSnapshot): List<Anomaly> {
        if (snapshot.isEmpty) return emptyList()
        val now = snapshot.capturedAtMillis
        val out = mutableListOf<Anomaly>()

        out += detectLightMismatch(snapshot, now)
        out += detectWiperMismatch(snapshot, now)
        out += detectTirePressure(snapshot, now)
        out += detectFluidLevels(snapshot, now)
        out += detectWasherFluid(snapshot, now)
        out += detectTelltales(snapshot, now)
        out += detectDoorOpenWhileDriving(snapshot, now)
        out += detectSeatBelt(snapshot, now)
        out += detectServiceInterval(snapshot, now)
        out += detectPowerDown(snapshot, now)

        return out.distinctBy { it.id }
    }

    // ── 1. 명령 − 실제 불일치 ────────────────────────────────────────────────

    private fun detectLightMismatch(s: VehicleSnapshot, now: Long): List<Anomaly> =
        s.lights.mapNotNull { (id, reading) ->
            val commanded = reading.switch == LightSwitchState.ON ||
                reading.switch == LightSwitchState.AUTOMATIC ||
                reading.switch == LightSwitchState.DAYTIME_RUNNING
            val lit = reading.actual == LightActualState.ON ||
                reading.actual == LightActualState.DAYTIME_RUNNING
            // 실제 상태를 못 읽은 경우는 "고장"이 아니라 "미지원"이므로 보고하지 않는다.
            if (!commanded || lit || reading.actual == LightActualState.UNKNOWN) {
                return@mapNotNull null
            }

            Anomaly(
                id = "light-" + id.name,
                part = id.part,
                category = PartCategory.LIGHTING,
                title = id.label + " 미점등",
                evidence = "점등 명령은 " + reading.switch.name +
                    " 인데 실제 점등 상태는 " + reading.actual.name + " 입니다",
                pattern = DetectionPattern.COMMAND_ACTUAL_MISMATCH,
                baseSeverity = BaseSeverity.LOW,
                detectedAtMillis = now,
            )
        }

    private fun detectWiperMismatch(s: VehicleSnapshot, now: Long): List<Anomaly> {
        val w = s.wiper ?: return emptyList()
        if (!w.switchOn || w.actualOn) return emptyList()
        return listOf(
            Anomaly(
                id = "wiper-mismatch",
                part = CarPart.WIPER,
                category = PartCategory.WIPER,
                title = "와이퍼 작동 불량",
                evidence = "작동 명령은 " + w.switchLabel + " 인데 실제 상태는 " + w.actualLabel + " 입니다",
                pattern = DetectionPattern.COMMAND_ACTUAL_MISMATCH,
                baseSeverity = BaseSeverity.LOW,
                detectedAtMillis = now,
            )
        )
    }

    // ── 2. 임계값 / 편차 ────────────────────────────────────────────────────

    private fun detectTirePressure(s: VehicleSnapshot, now: Long): List<Anomaly> {
        if (s.tires.isEmpty()) return emptyList()
        val out = mutableListOf<Anomaly>()

        // 임계값 미만
        s.tires.forEach { (part, reading) ->
            val threshold = reading.lowThresholdKpa?.takeIf { it > 0f } ?: DEFAULT_LOW_PRESSURE_KPA
            if (reading.pressureKpa > 0.1f && reading.pressureKpa < threshold) {
                out += Anomaly(
                    id = "tire-low-" + part.name,
                    part = part,
                    category = PartCategory.TIRE,
                    title = part.label + " 공기압 저하",
                    evidence = "현재 " + fmt(reading.pressureKpa) + " kPa — 기준 " +
                        fmt(threshold) + " kPa 미만",
                    pattern = DetectionPattern.THRESHOLD,
                    baseSeverity = BaseSeverity.MEDIUM,
                    detectedAtMillis = now,
                )
            }
        }

        // 4륜 편차
        val values = s.tires.values.map { it.pressureKpa }.filter { it > 0.1f }
        if (values.size >= 2) {
            val max = values.max()
            val min = values.min()
            if (max > 0f && (max - min) / max > PRESSURE_DEVIATION_RATIO) {
                val lowest = s.tires.entries
                    .filter { it.value.pressureKpa > 0.1f }
                    .minByOrNull { it.value.pressureKpa }
                // 이미 임계값 미만으로 잡힌 바퀴면 중복 보고하지 않는다.
                if (lowest != null && out.none { it.id == "tire-low-" + lowest.key.name }) {
                    val pct = (100f * (max - min) / max).toInt()
                    out += Anomaly(
                        id = "tire-deviation",
                        part = lowest.key,
                        category = PartCategory.TIRE,
                        title = "타이어 공기압 편차 과다",
                        evidence = "4륜 최대 " + fmt(max) + " kPa / 최소 " + fmt(min) +
                            " kPa — 편차 " + pct + "%",
                        pattern = DetectionPattern.DEVIATION,
                        baseSeverity = BaseSeverity.MEDIUM,
                        detectedAtMillis = now,
                    )
                }
            }
        }
        return out
    }

    private fun detectFluidLevels(s: VehicleSnapshot, now: Long): List<Anomaly> =
        s.fluids.mapNotNull { (id, level) ->
            val severity = when (level) {
                FluidLevel.CRITICALLY_LOW -> BaseSeverity.CRITICAL
                // 브레이크액은 안전 핵심이라 LOW 부터 CRITICAL 로 본다.
                FluidLevel.LOW -> if (id.part == CarPart.BRAKE) BaseSeverity.CRITICAL else BaseSeverity.MEDIUM
                FluidLevel.ERROR -> BaseSeverity.MEDIUM
                else -> return@mapNotNull null
            }
            val category = if (id.part == CarPart.BRAKE) PartCategory.BRAKING else PartCategory.POWERTRAIN
            val levelLabel = when (level) {
                FluidLevel.CRITICALLY_LOW -> "매우 부족"
                FluidLevel.LOW -> "부족"
                FluidLevel.ERROR -> "센서 오류"
                else -> level.name
            }
            Anomaly(
                id = "fluid-" + id.name,
                part = id.part,
                category = category,
                title = id.label + " " + levelLabel,
                evidence = id.label + " 레벨 센서가 " + levelLabel + " 상태를 보고했습니다",
                pattern = DetectionPattern.THRESHOLD,
                baseSeverity = severity,
                detectedAtMillis = now,
            )
        }

    private fun detectWasherFluid(s: VehicleSnapshot, now: Long): List<Anomaly> {
        if (s.lowWasherFluid != true) return emptyList()
        return listOf(
            Anomaly(
                id = "washer-low",
                part = CarPart.WASHER,
                category = PartCategory.CONSUMABLE,
                title = "워셔액 부족",
                evidence = "워셔액 부족 경고가 활성 상태입니다",
                pattern = DetectionPattern.THRESHOLD,
                baseSeverity = BaseSeverity.LOW,
                detectedAtMillis = now,
            )
        )
    }

    // ── 3. 경고등 ───────────────────────────────────────────────────────────

    private fun detectTelltales(s: VehicleSnapshot, now: Long): List<Anomaly> =
        s.telltales.mapNotNull { t ->
            if (t.state != TelltaleStateValue.ON && t.state != TelltaleStateValue.BLINK) {
                return@mapNotNull null
            }
            val spec = TelltaleCatalog.lookup(t.type) ?: return@mapNotNull null

            // 깜빡임은 상시 점등보다 긴급한 신호로 본다 (한 단계 상향, CRITICAL 이 상한).
            val severity = if (t.state == TelltaleStateValue.BLINK) {
                BaseSeverity.entries[minOf(spec.severity.ordinal + 1, BaseSeverity.entries.lastIndex)]
            } else {
                spec.severity
            }
            val stateWord = if (t.state == TelltaleStateValue.BLINK) "깜빡이고" else "점등되어"

            Anomaly(
                id = "telltale-" + t.type,
                part = spec.part,
                category = spec.category,
                title = spec.title,
                evidence = spec.lampName + " 경고등이 " + stateWord + " 있습니다",
                pattern = DetectionPattern.TELLTALE,
                baseSeverity = severity,
                detectedAtMillis = now,
            )
        }

    // ── 4. 상태 조합 ────────────────────────────────────────────────────────

    private fun detectDoorOpenWhileDriving(s: VehicleSnapshot, now: Long): List<Anomaly> {
        if (s.openDoors.isEmpty()) return emptyList()
        if (!isMoving(s)) return emptyList()
        return listOf(
            Anomaly(
                id = "door-open-driving",
                part = CarPart.DOOR,
                category = PartCategory.BODY,
                title = "도어 열림 상태로 주행",
                evidence = s.openDoors.joinToString(", ") + " 가 열린 상태에서 주행 중입니다",
                pattern = DetectionPattern.COMBINATION,
                baseSeverity = BaseSeverity.MEDIUM,
                detectedAtMillis = now,
            )
        )
    }

    private fun detectSeatBelt(s: VehicleSnapshot, now: Long): List<Anomaly> {
        val unbuckled = s.seatBeltWarnings.filterValues { it }.keys
        if (unbuckled.isEmpty()) return emptyList()
        if (!isMoving(s)) return emptyList()
        return listOf(
            Anomaly(
                id = "seatbelt-unbuckled",
                part = CarPart.SEATBELT,
                category = PartCategory.SAFETY,
                title = "안전벨트 미착용",
                evidence = unbuckled.joinToString(", ") + " 좌석 안전벨트 미착용 경고가 활성입니다",
                pattern = DetectionPattern.COMBINATION,
                baseSeverity = BaseSeverity.MEDIUM,
                detectedAtMillis = now,
            )
        )
    }

    private fun detectPowerDown(s: VehicleSnapshot, now: Long): List<Anomaly> {
        if (s.powerDownWarning != true) return emptyList()
        return listOf(
            Anomaly(
                id = "power-down",
                part = CarPart.BATTERY,
                category = PartCategory.ENERGY,
                title = "전원 차단 경고",
                evidence = "전원 다운 경고 표시가 활성 상태입니다",
                pattern = DetectionPattern.TELLTALE,
                baseSeverity = BaseSeverity.CRITICAL,
                detectedAtMillis = now,
            )
        )
    }

    // ── 5. 추이 ─────────────────────────────────────────────────────────────

    private fun detectServiceInterval(s: VehicleSnapshot, now: Long): List<Anomaly> {
        val odo = s.odometerKm ?: return emptyList()
        if (odo <= 0f) return emptyList()
        val remaining = SERVICE_INTERVAL_KM - (odo % SERVICE_INTERVAL_KM)
        if (remaining > 500f) return emptyList()
        return listOf(
            Anomaly(
                id = "service-due",
                part = CarPart.ENGINE,
                category = PartCategory.CONSUMABLE,
                title = "소모품 교체 주기 도래",
                evidence = "누적 주행 " + fmt(odo) + " km — 다음 정기점검까지 약 " +
                    fmt(remaining) + " km 남았습니다",
                pattern = DetectionPattern.TREND,
                baseSeverity = BaseSeverity.LOW,
                detectedAtMillis = now,
            )
        )
    }

    private fun isMoving(s: VehicleSnapshot): Boolean =
        s.gear == GearState.DRIVE || (s.estimatedSpeedKmh ?: 0f) > 1f

    private fun fmt(v: Float): String =
        if (abs(v - v.toInt()) < 0.05f) v.toInt().toString() else String.format("%.1f", v)
}
