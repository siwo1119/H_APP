package com.cardoctor.advisor.domain.model

/**
 * 등급 산정에 쓰이는 현재 주행 환경.
 * 기획안의 "환경 가중치"는 전부 이 객체에서 나온다.
 */
data class DrivingContext(
    val daylight: Daylight = Daylight.UNKNOWN,
    val weather: Weather = Weather.UNKNOWN,
    val roadType: RoadType = RoadType.UNKNOWN,
    val longTrip: Boolean = false,
    val ambientTempC: Float? = null,
    val speedKmh: Float? = null,
    /** 시연용 수동 오버라이드가 걸려 있는지 — 화면에 배지로 표시해 심사 시 투명하게 둔다. */
    val overridden: Boolean = false,
    val locationName: String? = null,
) {
    val isNight: Boolean get() = daylight == Daylight.NIGHT
    val isWet: Boolean get() = weather == Weather.RAIN || weather == Weather.SNOW
    val isHighway: Boolean get() = roadType == RoadType.HIGHWAY
    val isExtremeTemp: Boolean
        get() = ambientTempC?.let { it >= 33f || it <= -5f } ?: false
}

enum class Daylight(val label: String) {
    DAY("낮"), NIGHT("밤"), UNKNOWN("—")
}

enum class Weather(val label: String) {
    CLEAR("맑음"), CLOUDY("흐림"), RAIN("비"), SNOW("눈"), FOG("안개"), UNKNOWN("—")
}

enum class RoadType(val label: String) {
    CITY("시내"), HIGHWAY("고속도로"), UNKNOWN("—")
}
