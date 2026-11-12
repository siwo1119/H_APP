package com.cardoctor.advisor.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.domain.model.Finding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.historyDataStore by preferencesDataStore(name = "diagnosis_history")

/**
 * 진단 이력 저장소.
 *
 * 같은 증상이 반복되는지(반복 고장) 보여주는 것이 목적이라, 전체 스냅샷이 아니라
 * 증상 단위 레코드만 남긴다. 저장은 Preferences DataStore 의 JSON 문자열 한 칸이다 —
 * 레코드 수가 [MAX_RECORDS] 로 묶여 있어 별도 DB 를 들일 이유가 없다.
 */
class HistoryStore(private val appContext: Context) {

    @Serializable
    data class Record(
        val anomalyId: String,
        val partName: String,
        val title: String,
        val evidence: String,
        val adviceLevel: Int,
        val urgencyScore: Int,
        val atMillis: Long,
    ) {
        val part: CarPart?
            get() = runCatching { CarPart.valueOf(partName) }.getOrNull()

        val advice: AdviceLevel
            get() = AdviceLevel.entries.firstOrNull { it.level == adviceLevel } ?: AdviceLevel.SCHEDULED
    }

    private val json = Json { ignoreUnknownKeys = true }

    val records: Flow<List<Record>> = appContext.historyDataStore.data
        .catch { e ->
            Log.w(TAG, "history read failed: ${e.message}")
            emit(emptyPreferences())
        }
        .map { prefs -> decode(prefs[KEY_RECORDS]) }

    /**
     * 새 진단 결과를 이력에 반영한다.
     *
     * 동일 증상이 [DEDUP_WINDOW_MS] 안에 다시 감지되면 새 레코드를 만들지 않는다.
     * 스캔 주기마다 같은 고장이 쌓여 이력이 의미를 잃는 것을 막기 위함이다.
     */
    suspend fun record(findings: List<Finding>, nowMillis: Long) {
        if (findings.isEmpty()) return
        runCatching {
            appContext.historyDataStore.edit { prefs ->
                val existing = decode(prefs[KEY_RECORDS])
                val additions = findings.mapNotNull { f ->
                    val recent = existing.firstOrNull {
                        it.anomalyId == f.anomaly.id && nowMillis - it.atMillis < DEDUP_WINDOW_MS
                    }
                    if (recent != null) return@mapNotNull null
                    Record(
                        anomalyId = f.anomaly.id,
                        partName = f.part.name,
                        title = f.title,
                        evidence = f.anomaly.evidence,
                        adviceLevel = f.adviceLevel.level,
                        urgencyScore = f.urgencyScore,
                        atMillis = nowMillis,
                    )
                }
                if (additions.isEmpty()) return@edit
                val merged = (additions + existing).take(MAX_RECORDS)
                prefs[KEY_RECORDS] = json.encodeToString(ListSerializer(Record.serializer()), merged)
            }
        }.onFailure { Log.w(TAG, "history write failed: ${it.message}") }
    }

    suspend fun clear() {
        runCatching { appContext.historyDataStore.edit { it.remove(KEY_RECORDS) } }
    }

    private fun decode(raw: String?): List<Record> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Record.serializer()), raw) }
            .getOrElse {
                Log.w(TAG, "history decode failed: ${it.message}")
                emptyList()
            }
    }

    private fun emptyPreferences(): Preferences =
        androidx.datastore.preferences.core.emptyPreferences()

    private companion object {
        const val TAG = "HistoryStore"
        val KEY_RECORDS = stringPreferencesKey("records_json")
        const val MAX_RECORDS = 100
        const val DEDUP_WINDOW_MS = 30 * 60 * 1000L
    }
}
