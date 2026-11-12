package com.cardoctor.advisor.data.env

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * 일출·일몰 시각 계산 (NOAA 약식 알고리즘).
 *
 * **네트워크를 쓰지 않는다.** 낮/밤 판정은 시나리오 A·B 의 분기 조건이라 시연 중 절대 실패하면
 * 안 되므로, API 대신 결정론적 계산으로 둔다.
 *
 * 정확도는 분 단위이며, 등급 판정에 쓰기에 충분하다.
 */
object SunCalculator {

    data class SunTimes(
        val sunriseMinutesOfDay: Int,
        val sunsetMinutesOfDay: Int,
        /** 극야·백야 등으로 계산이 불가능한 경우 true. 이때는 낮으로 간주한다. */
        val polar: Boolean = false,
    )

    /**
     * @param latitude  위도 (북위 +)
     * @param longitude 경도 (동경 +)
     */
    fun calculate(
        timeMillis: Long,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): SunTimes {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timeMillis }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        // 해당 시점의 UTC 오프셋 (시간 단위) — 서머타임 적용분 포함
        val tzOffsetHours = timeZone.getOffset(timeMillis) / 3_600_000.0

        val sunrise = computeEvent(dayOfYear, latitude, longitude, tzOffsetHours, rising = true)
        val sunset = computeEvent(dayOfYear, latitude, longitude, tzOffsetHours, rising = false)

        if (sunrise == null || sunset == null) {
            return SunTimes(sunriseMinutesOfDay = 0, sunsetMinutesOfDay = 24 * 60, polar = true)
        }
        return SunTimes(
            sunriseMinutesOfDay = sunrise,
            sunsetMinutesOfDay = sunset,
        )
    }

    fun isNight(
        timeMillis: Long,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val times = calculate(timeMillis, latitude, longitude, timeZone)
        if (times.polar) return false
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timeMillis }
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return minutes < times.sunriseMinutesOfDay || minutes >= times.sunsetMinutesOfDay
    }

    /** @return 자정 기준 분. 계산 불가(극야/백야)면 null. */
    private fun computeEvent(
        dayOfYear: Int,
        latitude: Double,
        longitude: Double,
        tzOffsetHours: Double,
        rising: Boolean,
    ): Int? {
        val zenith = 90.833 // 태양 원반 상단 + 대기 굴절 보정
        val lngHour = longitude / 15.0

        val t = if (rising) {
            dayOfYear + ((6.0 - lngHour) / 24.0)
        } else {
            dayOfYear + ((18.0 - lngHour) / 24.0)
        }

        // 태양의 평균 근점 이각
        val m = (0.9856 * t) - 3.289
        // 태양의 실제 황경
        var l = m + (1.916 * sin(m.rad)) + (0.020 * sin(2 * m.rad)) + 282.634
        l = l.normalizeDeg()

        // 적경
        var ra = Math.toDegrees(Math.atan(0.91764 * tan(l.rad)))
        ra = ra.normalizeDeg()
        // 적경을 황경과 같은 사분면으로 맞춘다
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra = (ra + (lQuadrant - raQuadrant)) / 15.0

        // 적위
        val sinDec = 0.39782 * sin(l.rad)
        val cosDec = cos(asin(sinDec))

        // 시간각
        val cosH = (cos(zenith.rad) - (sinDec * sin(latitude.rad))) / (cosDec * cos(latitude.rad))
        if (cosH > 1.0 || cosH < -1.0) return null // 그날 뜨지 않거나 지지 않음

        val h = if (rising) {
            (360.0 - Math.toDegrees(acos(cosH))) / 15.0
        } else {
            Math.toDegrees(acos(cosH)) / 15.0
        }

        val localMeanTime = h + ra - (0.06571 * t) - 6.622
        var utc = (localMeanTime - lngHour) % 24.0
        if (utc < 0) utc += 24.0

        var local = utc + tzOffsetHours
        local = ((local % 24.0) + 24.0) % 24.0

        return (local * 60.0).toInt().coerceIn(0, 24 * 60 - 1)
    }

    private val Double.rad: Double get() = Math.toRadians(this)

    private fun Double.normalizeDeg(): Double {
        var v = this % 360.0
        if (v < 0) v += 360.0
        return v
    }
}
