package com.cardoctor.advisor.platform

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AAOS 주행 중 UX 제한 감시.
 *
 * 주행 중에는 시스템이 텍스트 길이·스크롤·동영상을 강제로 제한한다. 기획안의 "주행 중에는
 * 음성·큰 알림 위주" 는 권장이 아니라 **플랫폼 의무**이므로, 긴 설명을 주행 중에 띄우려 하면
 * 시스템이 차단한다. 따라서 UI 는 항상 이 상태를 보고 2단(요약/상세)으로 갈라진다.
 *
 * `android.car` 는 선택적 시스템 라이브러리라 (`useLibrary("android.car")`), 비 AAOS 기기나
 * 라이브러리 미탑재 환경에서는 조용히 "제한 없음" 으로 동작한다.
 */
class DrivingStateMonitor(private val appContext: Context) {

    data class State(
        /** 주의 분산 최소화가 요구되는 상태 = 주행 중으로 간주 */
        val distractionOptimizationRequired: Boolean = false,
        /** 시스템이 허용하는 최대 문자열 길이. 0 이하면 제한 없음. */
        val maxStringLength: Int = 0,
    ) {
        val isDriving: Boolean get() = distractionOptimizationRequired

        /** 주행 중 노출 가능한 길이로 자른다. */
        fun restrict(text: String): String {
            if (!distractionOptimizationRequired) return text
            val limit = if (maxStringLength > 0) maxStringLength else DEFAULT_DRIVING_LIMIT
            return if (text.length <= limit) text else text.take(limit - 1).trimEnd() + "…"
        }

        private companion object {
            /** 시스템이 길이를 알려주지 않을 때의 보수적 기본값 (AAOS 통상값). */
            const val DEFAULT_DRIVING_LIMIT = 120
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        if (car != null) return
        runCatching {
            val c = Car.createCar(appContext.applicationContext)
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            manager = m
            if (m == null) {
                Log.i(TAG, "CarUxRestrictionsManager unavailable — treating as unrestricted")
                return
            }
            m.registerListener { restrictions -> apply(restrictions) }
            apply(m.currentCarUxRestrictions)
            Log.i(TAG, "CarUxRestrictions monitoring started")
        }.onFailure {
            // 비 AAOS 환경 혹은 car 라이브러리 미탑재 — 제한 없음으로 둔다.
            Log.i(TAG, "android.car unavailable (${it.javaClass.simpleName}) — unrestricted")
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private fun apply(restrictions: CarUxRestrictions?) {
        if (restrictions == null) return
        _state.value = State(
            distractionOptimizationRequired = restrictions.isRequiresDistractionOptimization,
            maxStringLength = restrictions.maxRestrictedStringLength,
        )
    }

    private companion object {
        const val TAG = "DrivingStateMonitor"
    }
}
