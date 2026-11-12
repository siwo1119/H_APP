package com.cardoctor.advisor.data.env

import com.cardoctor.advisor.domain.model.Daylight
import com.cardoctor.advisor.domain.model.DrivingContext
import com.cardoctor.advisor.domain.model.RoadType
import com.cardoctor.advisor.domain.model.VehicleSnapshot
import com.cardoctor.advisor.domain.model.Weather
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 현재 주행 환경을 조립한다. 심각도 엔진이 쓰는 [DrivingContext] 의 유일한 출처.
 *
 * - 낮/밤   : [SunCalculator] 로 오프라인 계산
 * - 날씨    : [WeatherClient] (Open-Meteo). 실패 시 UNKNOWN
 * - 도로 종류: 추정 속도로 판정. SDK 에 차속·도로 API 가 없어 파생값을 쓴다
 * - 장거리  : 주행 가능 거리 잔량 또는 누적 주행 시간
 *
 * **시연 오버라이드**: 심사장에서 밤↔낮, 맑음↔비를 즉석 전환할 수 있도록 수동 오버라이드를 둔다.
 * 오버라이드가 걸리면 [DrivingContext.overridden] 이 true 가 되고 화면에 배지로 표시되므로,
 * 실측값인지 시연값인지 보는 사람이 항상 구분할 수 있다.
 */
class EnvironmentRepository(
    private val weatherClient: WeatherClient = WeatherClient(),
) {

    data class Override(
        val daylight: Daylight? = null,
        val weather: Weather? = null,
        val roadType: RoadType? = null,
    ) {
        val isActive: Boolean get() = daylight != null || weather != null || roadType != null
    }

    private val _override = MutableStateFlow(Override())
    val override: StateFlow<Override> = _override.asStateFlow()

    private var cachedWeather: WeatherClient.WeatherReading? = null
    private var cachedWeatherAtMillis: Long = 0L

    fun setOverride(update: (Override) -> Override) {
        _override.value = update(_override.value)
    }

    fun clearOverride() {
        _override.value = Override()
    }

    suspend fun current(snapshot: VehicleSnapshot, nowMillis: Long): DrivingContext {
        val ov = _override.value

        val measuredWeather = refreshWeatherIfStale(nowMillis)
        val measuredDaylight = if (
            SunCalculator.isNight(nowMillis, DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        ) {
            Daylight.NIGHT
        } else {
            Daylight.DAY
        }
        val measuredRoad = roadTypeFrom(snapshot.estimatedSpeedKmh)

        return DrivingContext(
            daylight = ov.daylight ?: measuredDaylight,
            weather = ov.weather ?: (measuredWeather?.weather ?: Weather.UNKNOWN),
            roadType = ov.roadType ?: measuredRoad,
            longTrip = isLongTrip(snapshot),
            ambientTempC = measuredWeather?.temperatureC,
            speedKmh = snapshot.estimatedSpeedKmh,
            overridden = ov.isActive,
            locationName = DEFAULT_LOCATION_NAME,
        )
    }

    private suspend fun refreshWeatherIfStale(nowMillis: Long): WeatherClient.WeatherReading? {
        val fresh = cachedWeather != null && (nowMillis - cachedWeatherAtMillis) < WEATHER_TTL_MS
        if (fresh) return cachedWeather

        val fetched = weatherClient.fetch(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        if (fetched != null) {
            cachedWeather = fetched
            cachedWeatherAtMillis = nowMillis
        }
        // 조회 실패 시 직전 캐시를 유지한다 — 일시적 네트워크 단절로 가중치가 흔들리지 않게.
        return cachedWeather
    }

    private fun roadTypeFrom(speedKmh: Float?): RoadType = when {
        speedKmh == null -> RoadType.UNKNOWN
        speedKmh >= HIGHWAY_SPEED_KMH -> RoadType.HIGHWAY
        else -> RoadType.CITY
    }

    private fun isLongTrip(snapshot: VehicleSnapshot): Boolean {
        val range = snapshot.rangeRemainingKm ?: return false
        // 주행 가능 거리가 넉넉히 남아 있고 주행 중이면 장거리 운행으로 본다.
        return range > LONG_TRIP_RANGE_KM && (snapshot.estimatedSpeedKmh ?: 0f) > 1f
    }

    fun close() = weatherClient.close()

    private companion object {
        /**
         * 기본 좌표 — 서울. 에뮬레이터는 실제 GPS 가 없고, Vehicle SDK 도 위치를 제공하지 않는다.
         * 실차 연동 시 LocationManager 또는 내비 SDK 좌표로 대체할 지점.
         */
        const val DEFAULT_LATITUDE = 37.5665
        const val DEFAULT_LONGITUDE = 126.9780
        const val DEFAULT_LOCATION_NAME = "서울"

        const val WEATHER_TTL_MS = 10 * 60 * 1000L
        const val HIGHWAY_SPEED_KMH = 80f
        const val LONG_TRIP_RANGE_KM = 150f
    }
}
