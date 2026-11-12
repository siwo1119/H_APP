package com.cardoctor.advisor.data.llm

import android.util.Log
import com.cardoctor.advisor.BuildConfig
import com.cardoctor.advisor.domain.model.DrivingContext
import com.cardoctor.advisor.domain.model.Finding
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 고장 설명 문장 생성.
 *
 * ## 설계 원칙 — 등급은 규칙, 설명만 LLM
 * 긴급도와 4단계 권고는 [com.cardoctor.advisor.domain.severity.SeverityEngine] 이 **이미 확정한 뒤**
 * 이 클래스에 전달된다. LLM 은 그 결과를 쉬운 말로 바꾸는 역할만 하며, 등급을 바꿀 수 없다.
 * 안전 관련 조언이므로 이 경계는 타협 대상이 아니다.
 *
 * ## 실패 처리
 * 키가 없거나, 네트워크가 끊기거나, 모델이 응답을 거절하면 [ExplanationTemplates] 로 폴백한다.
 * 설명이 비어 있는 화면은 나오지 않는다.
 *
 * ## API 키
 * 개발 중에는 `local.properties` 의 `llm.apiKey` 를 쓴다 (git 에 올라가지 않는다).
 * **배포 시에는 `llm.baseUrl` 을 자체 프록시로 지정해 키를 앱에서 제거해야 한다.**
 * APK 에 담긴 키는 추출 가능하다.
 */
class LlmExplainer(
    private val apiKey: String = BuildConfig.LLM_API_KEY,
    private val model: String = BuildConfig.LLM_MODEL,
    private val baseUrl: String = ANTHROPIC_ENDPOINT,
) {

    data class Explanation(
        /** 주행 중에도 띄울 수 있는 한 줄 요약. CarUxRestrictions 길이 제한을 염두에 둔다. */
        val summary: String,
        /** 정차 중에만 여는 상세 설명. */
        val detail: String,
        val fromLlm: Boolean,
    )

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
            }
        }
    }

    suspend fun explain(finding: Finding, context: DrivingContext): Explanation {
        val fallback = Explanation(
            summary = ExplanationTemplates.summary(finding, context),
            detail = ExplanationTemplates.detail(finding, context),
            fromLlm = false,
        )
        if (!isConfigured) return fallback

        return withContext(Dispatchers.IO) {
            runCatching { requestExplanation(finding, context) }
                .getOrElse { e ->
                    Log.w(TAG, "LLM explain failed: ${e.message}")
                    null
                } ?: fallback
        }
    }

    private suspend fun requestExplanation(finding: Finding, context: DrivingContext): Explanation? {
        val request = MessagesRequest(
            model = model,
            maxTokens = MAX_TOKENS,
            system = SYSTEM_PROMPT,
            // 짧은 설명 생성이라 깊은 추론이 필요 없다. 사고는 켠 채 effort 만 낮춰
            // 지연을 줄인다 (thinking 을 끄면 태그 누출·도구 호출 오작동 사례가 있다).
            outputConfig = OutputConfig(effort = "low"),
            messages = listOf(Message(role = "user", content = buildUserPrompt(finding, context))),
        )

        val response: MessagesResponse = client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            header(HEADER_API_KEY, apiKey)
            header(HEADER_VERSION, ANTHROPIC_VERSION)
            setBody(request)
        }.body()

        // 안전 분류기가 거절하면 content 가 비어 있을 수 있다 — 템플릿으로 폴백시킨다.
        if (response.stopReason == "refusal") {
            Log.w(TAG, "LLM refused: ${response.stopDetails?.category}")
            return null
        }

        val text = response.content
            .filter { it.type == "text" }
            .mapNotNull { it.text }
            .joinToString("\n")
            .trim()

        if (text.isBlank()) return null
        return parse(text)
    }

    /**
     * `요약:` / `상세:` 두 줄 규약으로 파싱한다.
     * 구조화 출력 대신 이 방식을 쓰는 이유는, 형식이 어긋나도 전체 텍스트를 상세 설명으로
     * 살릴 수 있어 절대 빈 화면이 되지 않기 때문이다.
     */
    private fun parse(text: String): Explanation {
        var summary = ""
        val detailLines = mutableListOf<String>()

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith(SUMMARY_PREFIX) ->
                    summary = line.removePrefix(SUMMARY_PREFIX).trim()
                line.startsWith(DETAIL_PREFIX) ->
                    detailLines += line.removePrefix(DETAIL_PREFIX).trim()
                line.isNotBlank() -> detailLines += line
            }
        }

        val detail = detailLines.joinToString(" ").trim().ifBlank { text }
        return Explanation(
            summary = summary.ifBlank { detail.take(SUMMARY_MAX_CHARS) },
            detail = detail,
            fromLlm = true,
        )
    }

    private fun buildUserPrompt(finding: Finding, context: DrivingContext): String {
        val weights = if (finding.appliedWeights.isEmpty()) {
            "없음"
        } else {
            finding.appliedWeights.joinToString(", ") { "${it.reason}(+${it.points})" }
        }
        return buildString {
            appendLine("아래는 차량 진단 시스템이 규칙 기반으로 이미 확정한 결과입니다.")
            appendLine()
            appendLine("- 부위: ${finding.part.label}")
            appendLine("- 증상: ${finding.title}")
            appendLine("- 판단 근거 신호: ${finding.anomaly.evidence}")
            appendLine("- 감지 방식: ${finding.anomaly.pattern.label}")
            appendLine("- 현재 환경: ${context.daylight.label}, ${context.weather.label}, ${context.roadType.label}")
            appendLine("- 적용된 환경 가중치: $weights")
            appendLine("- 확정 등급: ${finding.adviceLevel.level}단계 (${finding.adviceLevel.title})")
            appendLine("- 확정 권고: ${finding.adviceLevel.message}")
            appendLine()
            append("위 등급과 권고를 그대로 전제하고, 운전자가 이해할 수 있는 말로 설명해 주세요.")
        }
    }

    fun close() = runCatching { client.close() }.let { }

    private companion object {
        const val TAG = "LlmExplainer"

        const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val HEADER_API_KEY = "x-api-key"
        const val HEADER_VERSION = "anthropic-version"

        const val MAX_TOKENS = 1024
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val SUMMARY_MAX_CHARS = 40

        const val SUMMARY_PREFIX = "요약:"
        const val DETAIL_PREFIX = "상세:"

        val SYSTEM_PROMPT = """
            당신은 차량 정비 설명 도우미입니다. 차에 익숙하지 않은 운전자에게 설명합니다.

            반드시 지킬 것:
            - 심각도 등급과 행동 권고는 이미 확정되어 입력으로 주어집니다. 절대 바꾸거나 반박하지 마세요.
            - 주어진 등급·권고와 다른 조치를 제안하지 마세요.
            - 진단을 새로 하지 말고, 주어진 근거 신호 범위 안에서만 설명하세요.
            - 확실하지 않은 원인은 단정하지 말고 "~일 수 있습니다" 로 쓰세요.
            - 전문 용어를 쓰면 괄호로 짧게 풀어 주세요.

            출력 형식 (정확히 두 줄):
            요약: 주행 중에도 읽을 수 있는 한 줄. 25자 이내. 무엇을 해야 하는지 중심으로.
            상세: 2~4문장. 원인 → 현재 상황에서 위험한 이유 → 해야 할 일 순서로.
        """.trimIndent()
    }
}

// ── Messages API wire 모델 ──────────────────────────────────────────────────

@Serializable
private data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    @SerialName("output_config") val outputConfig: OutputConfig? = null,
    val messages: List<Message>,
)

@Serializable
private data class OutputConfig(val effort: String)

@Serializable
private data class Message(val role: String, val content: String)

@Serializable
private data class MessagesResponse(
    val content: List<ContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("stop_details") val stopDetails: StopDetails? = null,
)

@Serializable
private data class ContentBlock(
    val type: String,
    val text: String? = null,
)

@Serializable
private data class StopDetails(
    val type: String? = null,
    val category: String? = null,
)
