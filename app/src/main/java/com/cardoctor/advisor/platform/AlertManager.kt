package com.cardoctor.advisor.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cardoctor.advisor.MainActivity
import com.cardoctor.advisor.R
import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.domain.model.Finding
import java.util.Locale

/**
 * 단계적 알림 — 확장 아이디어 A.
 *
 * 등급이 오를수록 알림 강도를 올린다. 해외 사례 문서의 Wake Me App(졸음 감지 시 소리 →
 * 볼륨 → 지인 연락으로 강도를 높임) 구조를 4단계 등급 체계에 그대로 매핑했다.
 *
 * | 등급 | 알림 |
 * |---|---|
 * | 1 여유 있게 정비    | 조용한 알림만 (소리 없음) |
 * | 2 목적지 후 정비    | 일반 알림 |
 * | 3 즉시 정비         | 높은 중요도 알림 + **음성 안내** |
 * | 4 즉시 정차         | 긴급 알림(헤드업) + **음성 안내** |
 *
 * ## 왜 음성인가
 * AAOS 는 주행 중 화면 텍스트를 시스템이 강제로 제한한다. 기획안의 "주행 중에는 음성 위주"는
 * 권장이 아니라 의무이므로, 3·4등급은 화면이 아니라 소리로 먼저 전달한다.
 *
 * ## 중복 방지
 * 진단은 4초마다 돌지만, **같은 증상이 같은 등급으로 머무는 동안에는 다시 울리지 않는다.**
 * 등급이 올라갔을 때만 다시 알린다 — 그래야 "단계적"이라는 말이 성립한다.
 */
class AlertManager(private val appContext: Context) {

    /** anomaly id → 마지막으로 알린 등급. 등급이 올라갈 때만 다시 알린다. */
    private val lastNotifiedLevel = mutableMapOf<String, Int>()

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val notificationManager: NotificationManager?
        get() = ContextCompat.getSystemService(appContext, NotificationManager::class.java)

    private val audioManager: AudioManager?
        get() = ContextCompat.getSystemService(appContext, AudioManager::class.java)

    fun start() {
        createChannels()
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val result = tts?.setLanguage(Locale.KOREAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 에뮬레이터에 한국어 음성 데이터가 없을 수 있다. 알림은 그대로 동작한다.
                    Log.i(TAG, "Korean TTS unavailable — notifications only")
                    ttsReady = false
                }
            } else {
                Log.i(TAG, "TTS init failed — notifications only")
            }
        }
    }

    fun stop() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
    }

    /**
     * 현재 진단 결과를 알림에 반영한다.
     * @param summaryOf 해당 증상의 한 줄 요약(없으면 기본 권고 문구를 쓴다)
     */
    fun notify(findings: List<Finding>, summaryOf: (Finding) -> String?) {
        // 사라진 증상은 기억에서 지운다 — 다시 생기면 처음부터 알린다.
        val liveIds = findings.map { it.anomaly.id }.toSet()
        lastNotifiedLevel.keys.retainAll(liveIds)

        findings.forEach { finding ->
            val level = finding.adviceLevel.level
            val previous = lastNotifiedLevel[finding.anomaly.id]
            // 처음 보거나, 등급이 올라갔을 때만 알린다.
            if (previous != null && level <= previous) return@forEach
            lastNotifiedLevel[finding.anomaly.id] = level

            val summary = summaryOf(finding) ?: finding.adviceLevel.message
            showNotification(finding, summary)
            if (level >= AdviceLevel.IMMEDIATE_SERVICE.level) {
                speak(finding, summary)
            }
        }
    }

    /** 사용자가 이력을 지우는 등으로 상태를 초기화할 때. */
    fun reset() {
        lastNotifiedLevel.clear()
        notificationManager?.cancelAll()
    }

    // ── 알림 ───────────────────────────────────────────────────────────────

    private fun createChannels() {
        val nm = notificationManager ?: return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_LOW,
                "정비 안내",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "여유 있게 정비하면 되는 항목" },
            NotificationChannel(
                CHANNEL_DEFAULT,
                "정비 권장",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "목적지 도착 후 점검이 필요한 항목" },
            NotificationChannel(
                CHANNEL_HIGH,
                "즉시 정비",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "가까운 정비소로 바로 가야 하는 항목" },
            NotificationChannel(
                CHANNEL_URGENT,
                "즉시 정차",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "주행을 멈춰야 하는 항목"
                enableVibration(true)
            },
        )
        channels.forEach { runCatching { nm.createNotificationChannel(it) } }
    }

    private fun channelFor(level: AdviceLevel): String = when (level) {
        AdviceLevel.SCHEDULED -> CHANNEL_LOW
        AdviceLevel.AFTER_ARRIVAL -> CHANNEL_DEFAULT
        AdviceLevel.IMMEDIATE_SERVICE -> CHANNEL_HIGH
        AdviceLevel.PULL_OVER -> CHANNEL_URGENT
    }

    private fun showNotification(finding: Finding, summary: String) {
        val nm = notificationManager ?: return
        val level = finding.adviceLevel

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            appContext,
            finding.anomaly.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(appContext, channelFor(level))
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("${level.level}단계 · ${level.title} — ${finding.title}")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(
                if (level == AdviceLevel.PULL_OVER) {
                    NotificationCompat.CATEGORY_ERROR
                } else {
                    NotificationCompat.CATEGORY_RECOMMENDATION
                }
            )
            .setPriority(
                when (level) {
                    AdviceLevel.SCHEDULED -> NotificationCompat.PRIORITY_LOW
                    AdviceLevel.AFTER_ARRIVAL -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_HIGH
                }
            )

        // 4등급은 주행 중에도 즉시 눈에 띄어야 한다.
        if (level == AdviceLevel.PULL_OVER) {
            builder.setFullScreenIntent(pending, true)
            builder.setOngoing(true)
        }

        runCatching { nm.notify(finding.anomaly.id.hashCode(), builder.build()) }
            .onFailure { Log.w(TAG, "notify failed: ${it.message}") }
    }

    // ── 음성 ───────────────────────────────────────────────────────────────

    private fun speak(finding: Finding, summary: String) {
        if (!ttsReady) return
        val engine = tts ?: return

        // 차량 오디오 포커스를 받아야 내비·음악 위로 안내가 들린다.
        val focus = requestAudioFocus()

        val text = "${finding.part.label} ${finding.title}. $summary"
        val params = android.os.Bundle().apply {
            putInt(
                TextToSpeech.Engine.KEY_PARAM_STREAM,
                AudioManager.STREAM_MUSIC,
            )
        }
        runCatching {
            engine.speak(text, TextToSpeech.QUEUE_ADD, params, finding.anomaly.id)
        }.onFailure { Log.w(TAG, "speak failed: ${it.message}") }

        focus?.let { request ->
            // 안내가 끝나는 시점을 정확히 알기 어려우므로 짧은 포커스로 잡았다가 바로 반환한다.
            audioManager?.abandonAudioFocusRequest(request)
        }
    }

    private fun requestAudioFocus(): AudioFocusRequest? {
        val am = audioManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        return runCatching {
            am.requestAudioFocus(request)
            request
        }.getOrNull()
    }

    private companion object {
        const val TAG = "AlertManager"
        const val CHANNEL_LOW = "cardoctor_level1"
        const val CHANNEL_DEFAULT = "cardoctor_level2"
        const val CHANNEL_HIGH = "cardoctor_level3"
        const val CHANNEL_URGENT = "cardoctor_level4"
    }
}
