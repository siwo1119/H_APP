package com.cardoctor.advisor.data.env

import android.util.Log
import com.cardoctor.advisor.domain.model.Weather
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 날씨 조회. **Open-Meteo** 를 쓴다 — API 키가 필요 없어 시연 환경 구성이 단순하다.
 *
 * 실패하면 [Weather.UNKNOWN] 을 돌려주고, 환경 가중치에서 날씨 항목만 빠진다.
 * 네트워크가 없어도 진단·등급 판정은 그대로 동작한다.
 */
class WeatherClient {

    @Serializable
    private data class Response(
        val current: Current? = null,
    )

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperatureC: Float? = null,
        @SerialName("weather_code") val weatherCode: Int? = null,
        @SerialName("precipitation") val precipitation: Float? = null,
    )

    data class WeatherReading(
        val weather: Weather,
        val temperatureC: Float?,
    )

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }

    suspend fun fetch(latitude: Double, longitude: Double): WeatherReading? =
        withContext(Dispatchers.IO) {
            runCatching {
                val res: Response = client.get(ENDPOINT) {
                    parameter("latitude", latitude)
                    parameter("longitude", longitude)
                    parameter("current", "temperature_2m,weather_code,precipitation")
                }.body()

                val current = res.current ?: return@runCatching null
                WeatherReading(
                    weather = mapWmoCode(current.weatherCode),
                    temperatureC = current.temperatureC,
                )
            }.getOrElse { e ->
                Log.w(TAG, "weather fetch failed: ${e.message}")
                null
            }
        }

    fun close() = runCatching { client.close() }.let { }

    /**
     * WMO Weather interpretation code → 앱 날씨 분류.
     * https://open-meteo.com/en/docs — 코드 표 기준.
     */
    private fun mapWmoCode(code: Int?): Weather = when (code) {
        null -> Weather.UNKNOWN
        0, 1 -> Weather.CLEAR
        2, 3 -> Weather.CLOUDY
        45, 48 -> Weather.FOG
        in 51..67 -> Weather.RAIN      // 이슬비·비·어는 비
        in 71..77 -> Weather.SNOW      // 눈·싸락눈
        in 80..82 -> Weather.RAIN      // 소나기
        85, 86 -> Weather.SNOW         // 소낙눈
        in 95..99 -> Weather.RAIN      // 뇌우
        else -> Weather.UNKNOWN
    }

    private companion object {
        const val TAG = "WeatherClient"
        const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val REQUEST_TIMEOUT_MS = 5_000L
    }
}
