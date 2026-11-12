package com.cardoctor.advisor.domain.model

/**
 * Vehicle SDK 의 도메인별 응답을 한 벌의 공통 스키마로 정규화한 것.
 * 감지·판정 로직은 SDK 타입을 직접 보지 않고 이 스냅샷만 본다 (테스트 가능성 + 실차/에뮬 차이 흡수).
 */
data class VehicleSnapshot(
    val capturedAtMillis: Long = 0L,
    val lights: Map<LightId, LightReading> = emptyMap(),
    val wiper: WiperReading? = null,
    val tires: Map<CarPart, TireReading> = emptyMap(),
    val fluids: Map<FluidId, FluidLevel> = emptyMap(),
    val lowWasherFluid: Boolean? = null,
    val powerDownWarning: Boolean? = null,
    val telltales: List<TelltaleReading> = emptyList(),
    val seatBeltWarnings: Map<String, Boolean> = emptyMap(),
    val openDoors: List<String> = emptyList(),
    val gear: GearState = GearState.UNKNOWN,
    val odometerKm: Float? = null,
    val rangeRemainingKm: Float? = null,
    val evStateOfChargePercent: Float? = null,
    val fuelPercent: Float? = null,
    val steeringAngleDeg: Float? = null,
    /** Odometer 델타로 산출한 추정 속도(km/h). SDK 에 차속 API 가 없어 파생값으로 둔다. */
    val estimatedSpeedKmh: Float? = null,
) {
    val isEmpty: Boolean
        get() = lights.isEmpty() && wiper == null && tires.isEmpty() &&
            fluids.isEmpty() && telltales.isEmpty() && odometerKm == null
}

enum class LightId(val label: String, val part: CarPart) {
    LOW_BEAM("로우빔 전조등", CarPart.HEADLAMP_LEFT),
    HIGH_BEAM("하이빔 전조등", CarPart.HEADLAMP_RIGHT),
    TAIL_LAMP("미등", CarPart.TAIL_LAMP),
    FRONT_FOG("전방 안개등", CarPart.FOG_LAMP),
    REAR_FOG("후방 안개등", CarPart.FOG_LAMP),
}

/** 명령(switch)과 실제(state)를 분리해 담는다 — 불일치 추론의 전제. */
data class LightReading(
    val switch: LightSwitchState,
    val actual: LightActualState,
)

enum class LightSwitchState { OFF, ON, DAYTIME_RUNNING, AUTOMATIC, UNKNOWN }
enum class LightActualState { OFF, ON, DAYTIME_RUNNING, UNKNOWN }

data class WiperReading(
    val switchOn: Boolean,
    val switchLabel: String,
    val actualOn: Boolean,
    val actualLabel: String,
)

data class TireReading(
    val pressureKpa: Float,
    /** SDK 가 주는 저압 임계값. null 이면 기본 임계값을 쓴다. */
    val lowThresholdKpa: Float?,
)

enum class FluidId(val label: String, val part: CarPart) {
    ENGINE_OIL("엔진 오일", CarPart.ENGINE),
    BRAKE_FLUID("브레이크액", CarPart.BRAKE),
    TRANSMISSION_OIL("변속기 오일", CarPart.TRANSMISSION),
}

enum class FluidLevel { NORMAL, HIGH, LOW, CRITICALLY_LOW, ERROR, UNKNOWN }

data class TelltaleReading(
    val type: String,
    val state: TelltaleStateValue,
    val priority: Int,
)

enum class TelltaleStateValue { OFF, ON, BLINK, UNKNOWN }

enum class GearState { UNKNOWN, PARK, REVERSE, NEUTRAL, DRIVE }
