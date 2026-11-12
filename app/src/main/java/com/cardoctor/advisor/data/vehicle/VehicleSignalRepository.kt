package com.cardoctor.advisor.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import android.content.Context
import android.util.Log
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.domain.model.FluidId
import com.cardoctor.advisor.domain.model.FluidLevel
import com.cardoctor.advisor.domain.model.GearState
import com.cardoctor.advisor.domain.model.LightActualState
import com.cardoctor.advisor.domain.model.LightId
import com.cardoctor.advisor.domain.model.LightReading
import com.cardoctor.advisor.domain.model.LightSwitchState
import com.cardoctor.advisor.domain.model.TelltaleReading
import com.cardoctor.advisor.domain.model.TelltaleStateValue
import com.cardoctor.advisor.domain.model.TireReading
import com.cardoctor.advisor.domain.model.VehicleSnapshot
import com.cardoctor.advisor.domain.model.WiperReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Vehicle SDK 2.3.2 래퍼. 21개 도메인 중 진단에 쓰는 것만 읽어 [VehicleSnapshot] 으로 정규화한다.
 *
 * 수명주기: [connect] → ... → [disconnect]. SDK 의 `initialize()` / `release()` 와 1:1 대응한다.
 *
 * 모든 읽기는 실패를 null 로 흡수한다 (§VehicleSdkExt). 에뮬레이터와 실차에서 가용 속성이
 * 다르므로, 미지원 속성 하나가 전체 스캔을 중단시키면 안 된다.
 */
class VehicleSignalRepository(private val appContext: Context) {

    private var vehicle: Vehicle? = null

    /** 직전 주행거리 측정값 — 속도 추정에 쓴다. SDK 에 차속 API 가 없어 파생한다. */
    private var lastOdometerKm: Float? = null
    private var lastOdometerAtMillis: Long = 0L
    private var lastEstimatedSpeedKmh: Float? = null

    val isConnected: Boolean get() = vehicle != null

    fun connect(): Boolean = runCatching {
        if (vehicle != null) return true
        val v = Vehicle(appContext.applicationContext)
        v.initialize()
        vehicle = v
        Log.i(TAG, "Vehicle SDK initialized")
        true
    }.getOrElse { e ->
        Log.e(TAG, "Vehicle SDK initialize failed", e)
        vehicle = null
        false
    }

    fun disconnect() {
        runCatching { vehicle?.release() }
            .onFailure { Log.w(TAG, "release failed", it) }
        vehicle = null
    }

    /**
     * 전 도메인을 한 번에 읽어 스냅샷을 만든다. 도메인별 읽기는 병렬로 수행한다.
     */
    suspend fun readSnapshot(nowMillis: Long): VehicleSnapshot = withContext(Dispatchers.IO) {
        val v = vehicle ?: return@withContext VehicleSnapshot(capturedAtMillis = nowMillis)

        // 도메인별 읽기를 동시에 띄운다. 느린 도메인 하나가 전체 스캔을 지연시키지 않도록.
        val lights = async { readLights(v) }
        val wiper = async { readWiper(v) }
        val tires = async { readTires(v) }
        val fluids = async { readFluids(v) }
        val washer = async {
            sdkCall<Boolean>("hasLowWasherFluidWarning") { ok, ng ->
                v.getSafety().hasLowWasherFluidWarning(ok, ng)
            }
        }
        val powerDown = async {
            sdkCall<Boolean>("hasPowerDownWarningIndicator") { ok, ng ->
                v.getSafety().hasPowerDownWarningIndicator(ok, ng)
            }
        }
        val telltales = async { readTelltales(v) }
        val seatBelts = async { readSeatBelts(v) }
        val doors = async { readOpenDoors(v) }
        val gear = async { readGear(v) }
        val odo = async {
            sdkCall<Float>("getCurrentDistanceDriven") { ok, ng ->
                v.getOdometer().getCurrentDistanceDriven(ok, ng)
            }
        }
        val range = async {
            sdkCall<Float>("getRangeRemainingDistance") { ok, ng ->
                v.getOdometer().getRangeRemainingDistance(ok, ng)
            }
        }
        val soc = async {
            sdkCall<Int>("getEvBatteryStateOfCharge") { ok, ng ->
                v.getEvBattery().getEvBatteryStateOfCharge(ok, ng)
            }
        }
        val fuel = async {
            sdkCall<Int>("getFuelPercent") { ok, ng ->
                v.getFuel().getFuelPercent(ok, ng)
            }
        }
        val steering = async {
            sdkCall<Float>("getCurrentSteeringWheelAngle") { ok, ng ->
                v.getSteeringWheel().getCurrentSteeringWheelAngle(ok, ng)
            }
        }

        val odometerKm = odo.await()
        VehicleSnapshot(
            capturedAtMillis = nowMillis,
            lights = lights.await(),
            wiper = wiper.await(),
            tires = tires.await(),
            fluids = fluids.await(),
            lowWasherFluid = washer.await(),
            powerDownWarning = powerDown.await(),
            telltales = telltales.await(),
            seatBeltWarnings = seatBelts.await(),
            openDoors = doors.await(),
            gear = gear.await(),
            odometerKm = odometerKm,
            rangeRemainingKm = range.await(),
            evStateOfChargePercent = soc.await()?.toFloat(),
            fuelPercent = fuel.await()?.toFloat(),
            steeringAngleDeg = steering.await(),
            estimatedSpeedKmh = estimateSpeed(odometerKm, nowMillis),
        )
    }

    // ── 도메인별 읽기 ───────────────────────────────────────────────────────

    private suspend fun readLights(v: Vehicle): Map<LightId, LightReading> {
        val light = v.getLight()
        val out = linkedMapOf<LightId, LightReading>()

        suspend fun add(
            id: LightId,
            getSwitch: ((LightSwitch?) -> Unit, (Exception) -> Unit) -> Unit,
            getState: ((LightState?) -> Unit, (Exception) -> Unit) -> Unit,
        ) {
            val sw = sdkCall<LightSwitch>("${id.name}.switch", getSwitch)
            val st = sdkCall<LightState>("${id.name}.state", getState)
            if (sw == null && st == null) return
            out[id] = LightReading(
                switch = sw.toDomain(),
                actual = st.toDomain(),
            )
        }

        add(
            LightId.LOW_BEAM,
            { ok, ng -> light.getLowBeamLightSwitch(ok, ng) },
            { ok, ng -> light.getCurrentLowBeamLightState(ok, ng) },
        )
        add(
            LightId.HIGH_BEAM,
            { ok, ng -> light.getHighBeamLightSwitch(ok, ng) },
            { ok, ng -> light.getCurrentHighBeamLightState(ok, ng) },
        )
        add(
            LightId.TAIL_LAMP,
            { ok, ng -> light.getTailLampLightSwitch(ok, ng) },
            { ok, ng -> light.getCurrentTailLampLightState(ok, ng) },
        )
        add(
            LightId.FRONT_FOG,
            { ok, ng -> light.getFrontFogLightSwitch(ok, ng) },
            { ok, ng -> light.getCurrentFrontFogLightState(ok, ng) },
        )
        add(
            LightId.REAR_FOG,
            { ok, ng -> light.getRearFogLightSwitch(ok, ng) },
            { ok, ng -> light.getCurrentRearFogLightState(ok, ng) },
        )
        return out
    }

    private suspend fun readWiper(v: Vehicle): WiperReading? {
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val sw = sdkCall<WiperSwitch>("wiper.switch") { ok, ng ->
            v.getWiper().getCurrentWiperSwitch(area, ok, ng)
        } ?: return null
        val st = sdkCall<WiperState>("wiper.state") { ok, ng ->
            v.getWiper().getCurrentWiperState(area, ok, ng)
        } ?: return null

        val switchOn = sw != WiperSwitch.OFF && sw != WiperSwitch.OTHER && sw != WiperSwitch.SERVICE
        val actualOn = st == WiperState.ON
        return WiperReading(
            switchOn = switchOn,
            switchLabel = sw.name,
            actualOn = actualOn,
            actualLabel = st.name,
        )
    }

    private suspend fun readTires(v: Vehicle): Map<CarPart, TireReading> {
        val tire = v.getTire()
        val out = linkedMapOf<CarPart, TireReading>()
        WHEELS.forEach { (part, value) ->
            val area = WheelArea(value)
            val pressure = sdkCall<Float>("tirePressure.${value.name}") { ok, ng ->
                tire.getTirePressure(area, ok, ng)
            } ?: return@forEach
            val low = sdkCall<Float>("lowTirePressure.${value.name}") { ok, ng ->
                tire.getLowTirePressure(area, ok, ng)
            }
            out[part] = TireReading(pressureKpa = pressure, lowThresholdKpa = low)
        }
        return out
    }

    private suspend fun readFluids(v: Vehicle): Map<FluidId, FluidLevel> {
        val safety = v.getSafety()
        val out = linkedMapOf<FluidId, FluidLevel>()

        sdkCall<OilLevelState>("engineOil") { ok, ng -> safety.getEngineOilLevelState(ok, ng) }
            ?.let { out[FluidId.ENGINE_OIL] = it.toDomain() }
        sdkCall<OilLevelState>("brakeFluid") { ok, ng -> safety.getBrakeFluidLevelState(ok, ng) }
            ?.let { out[FluidId.BRAKE_FLUID] = it.toDomain() }
        sdkCall<OilLevelState>("transmissionOil") { ok, ng -> safety.getTransmissionOilLevelState(ok, ng) }
            ?.let { out[FluidId.TRANSMISSION_OIL] = it.toDomain() }

        return out
    }

    private suspend fun readTelltales(v: Vehicle): List<TelltaleReading> {
        val telltales = sdkCall<Telltales>("getTelltalesInfo") { ok, ng ->
            v.getSafety().getTelltalesInfo(ok, ng)
        } ?: return emptyList()

        return telltales.telltales.map { t ->
            TelltaleReading(
                type = t.type.name,
                state = when (t.state.name) {
                    "ON" -> TelltaleStateValue.ON
                    "BLINK" -> TelltaleStateValue.BLINK
                    "OFF" -> TelltaleStateValue.OFF
                    else -> TelltaleStateValue.UNKNOWN
                },
                priority = t.priority,
            )
        }
    }

    private suspend fun readSeatBelts(v: Vehicle): Map<String, Boolean> {
        val out = linkedMapOf<String, Boolean>()
        FRONT_SEATS.forEach { (label, value) ->
            val area = SeatArea(value)
            val warn = sdkCall<Boolean>("seatBelt.${value.name}") { ok, ng ->
                v.getSafety().hasSeatBeltNotBuckledWarning(area, ok, ng)
            } ?: return@forEach
            if (warn) out[label] = true
        }
        return out
    }

    private suspend fun readOpenDoors(v: Vehicle): List<String> {
        val out = mutableListOf<String>()
        DOORS.forEach { (label, value) ->
            val area = DoorArea(value)
            val position = sdkCall<Int>("doorPosition.${value.name}") { ok, ng ->
                v.getDoor().getCurrentDoorPosition(area, ok, ng)
            } ?: return@forEach
            if (position > 0) out += label
        }
        return out
    }

    private suspend fun readGear(v: Vehicle): GearState {
        val gear = sdkCall<VehicleGear>("gear") { ok, ng ->
            v.getGear().getCurrentGearSelectionState(ok, ng)
        } ?: return GearState.UNKNOWN
        return when (gear) {
            VehicleGear.GEAR_PARK -> GearState.PARK
            VehicleGear.GEAR_REVERSE -> GearState.REVERSE
            VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
            VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
            else -> GearState.DRIVE
        }
    }

    /**
     * SDK 에 차속 API 가 없어 주행거리 델타로 추정한다.
     * 샘플 간격이 너무 짧거나(노이즈) 역행하면 직전 값을 유지한다.
     */
    private fun estimateSpeed(odometerKm: Float?, nowMillis: Long): Float? {
        val prevKm = lastOdometerKm
        val prevAt = lastOdometerAtMillis

        if (odometerKm != null) {
            if (prevKm != null && prevAt > 0L) {
                val dtHours = (nowMillis - prevAt) / 3_600_000.0
                val dKm = odometerKm - prevKm
                if (dtHours > 0.0002 && dKm >= 0f) { // 약 0.7초 이상 간격만 채택
                    lastEstimatedSpeedKmh = (dKm / dtHours).toFloat().coerceIn(0f, 300f)
                }
            }
            lastOdometerKm = odometerKm
            lastOdometerAtMillis = nowMillis
        }
        return lastEstimatedSpeedKmh
    }

    private companion object {
        const val TAG = "VehicleSignalRepo"

        val WHEELS = listOf(
            CarPart.TIRE_FL to WheelValue.LEFT_FRONT,
            CarPart.TIRE_FR to WheelValue.RIGHT_FRONT,
            CarPart.TIRE_RL to WheelValue.LEFT_REAR,
            CarPart.TIRE_RR to WheelValue.RIGHT_REAR,
        )

        val FRONT_SEATS = listOf(
            "운전석" to SeatValue.ROW_1_LEFT,
            "동승석" to SeatValue.ROW_1_RIGHT,
        )

        val DOORS = listOf(
            "운전석 도어" to DoorValue.ROW_1_LEFT,
            "동승석 도어" to DoorValue.ROW_1_RIGHT,
            "좌측 뒷문" to DoorValue.ROW_2_LEFT,
            "우측 뒷문" to DoorValue.ROW_2_RIGHT,
            "트렁크" to DoorValue.REAR,
            "보닛" to DoorValue.HOOD,
        )
    }
}

// ── SDK enum → 도메인 enum ──────────────────────────────────────────────────

private fun LightSwitch?.toDomain(): LightSwitchState = when (this) {
    LightSwitch.OFF -> LightSwitchState.OFF
    LightSwitch.ON -> LightSwitchState.ON
    LightSwitch.DAYTIME_RUNNING -> LightSwitchState.DAYTIME_RUNNING
    LightSwitch.AUTOMATIC -> LightSwitchState.AUTOMATIC
    null -> LightSwitchState.UNKNOWN
}

private fun LightState?.toDomain(): LightActualState = when (this) {
    LightState.OFF -> LightActualState.OFF
    LightState.ON -> LightActualState.ON
    LightState.DAYTIME_RUNNING -> LightActualState.DAYTIME_RUNNING
    null -> LightActualState.UNKNOWN
}

private fun OilLevelState.toDomain(): FluidLevel = when (this) {
    OilLevelState.NORMAL -> FluidLevel.NORMAL
    OilLevelState.HIGH -> FluidLevel.HIGH
    OilLevelState.LOW -> FluidLevel.LOW
    OilLevelState.CRITICALLY_LOW -> FluidLevel.CRITICALLY_LOW
    OilLevelState.ERROR -> FluidLevel.ERROR
}
