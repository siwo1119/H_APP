package com.cardoctor.advisor.data.vehicle

import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Pleos Vehicle SDK 는 전부 `(onComplete, onFailed)` 콜백 쌍을 받는다.
 * 이 파일은 그 패턴을 코루틴으로 감싸, 호출부가 순차 코드로 읽히게 한다.
 *
 * 설계상 **실패는 예외가 아니라 null** 이다. 에뮬레이터와 실차의 가용 속성이 다르고
 * (샘플 저장소 README 명시 사항), 미지원 속성 하나가 전체 스캔을 깨뜨리면 안 되기 때문이다.
 * 미지원 → null → 해당 항목만 진단에서 빠진다.
 */
private const val TAG = "VehicleSdk"

/**
 * `get(onComplete, onFailed)` 형태를 감싼다.
 *
 * SDK 의 onComplete 파라미터는 **nullable** 이다 (`(Float?) -> Unit` 등). 값을 못 읽은 경우와
 * 실패한 경우가 모두 null 로 수렴하므로, 호출부는 한 가지 분기만 보면 된다.
 */
suspend fun <T : Any> sdkCall(
    name: String,
    block: (onComplete: (T?) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
): T? = suspendCancellableCoroutine { cont ->
    var resumed = false
    fun finish(value: T?) {
        if (!resumed) {
            resumed = true
            cont.resume(value)
        }
    }
    try {
        block(
            { result -> finish(result) },
            { e ->
                Log.d(TAG, "$name unavailable: ${e.message}")
                finish(null)
            },
        )
    } catch (e: Throwable) {
        // SDK 가 서비스 미연결 상태에서 동기 예외를 던지는 경우까지 흡수한다.
        Log.d(TAG, "$name threw: ${e.message}")
        finish(null)
    }
}
