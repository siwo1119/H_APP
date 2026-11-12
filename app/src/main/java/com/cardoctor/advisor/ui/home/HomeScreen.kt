package com.cardoctor.advisor.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.domain.model.Daylight
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.domain.model.RoadType
import com.cardoctor.advisor.domain.model.Weather
import com.cardoctor.advisor.ui.DiagnosisUiState
import com.cardoctor.advisor.ui.car.CarDiagram
import com.cardoctor.advisor.ui.common.DemoToggle
import com.cardoctor.advisor.ui.common.EnvChip
import com.cardoctor.advisor.ui.common.LevelBadge
import com.cardoctor.advisor.ui.common.SectionCard
import com.cardoctor.advisor.ui.common.color

/**
 * 홈 — 시연의 메인 화면.
 *
 * 좌측에 차량 도해, 우측에 종합 상태·환경·진단 목록을 둔다. 2560×1440 가로 화면을 전제로 한
 * 좌우 분할이며, 기획안의 "홈 화면 하나만 봐도 차 상태와 할 일이 보이게 한다"를 따른다.
 */
@Composable
fun HomeScreen(
    state: DiagnosisUiState,
    onPartClick: (com.cardoctor.advisor.domain.model.CarPart) -> Unit,
    onFindingClick: (Finding) -> Unit,
    onRunScan: () -> Unit,
    onOpenAdvice: () -> Unit,
    onOverrideDaylight: (Daylight?) -> Unit,
    onOverrideWeather: (Weather?) -> Unit,
    onOverrideRoad: (RoadType?) -> Unit,
    onClearOverrides: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── 좌: 차량 도해 ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                CarDiagram(
                    statusOf = state::statusOf,
                    selectedPart = state.selectedPart,
                    onPartClick = onPartClick,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
                Text(
                    text = "부위를 누르면 상세 진단을 볼 수 있습니다",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        }

        // ── 우: 상태 + 환경 + 목록 ─────────────────────────────────────────
        Column(
            modifier = Modifier.width(880.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OverallStatusCard(state, onRunScan, onOpenAdvice)
            EnvironmentCard(
                state = state,
                onOverrideDaylight = onOverrideDaylight,
                onOverrideWeather = onOverrideWeather,
                onOverrideRoad = onOverrideRoad,
                onClearOverrides = onClearOverrides,
            )
            FindingsCard(state, onFindingClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverallStatusCard(
    state: DiagnosisUiState,
    onRunScan: () -> Unit,
    onOpenAdvice: () -> Unit,
) {
    val worst = state.worstLevel
    SectionCard(
        title = "차량 종합 상태",
        trailing = { LevelBadge(worst) },
    ) {
        val message = when {
            !state.connected -> state.connectionMessage ?: "차량에 연결되지 않았습니다"
            state.findings.isEmpty() -> "점검한 항목에서 이상이 발견되지 않았습니다"
            else -> "${state.findings.size}건의 이상이 감지됐습니다"
        }
        Text(message, style = MaterialTheme.typography.bodyLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRunScan) { Text("전체 점검") }
            if (state.hasCriticalFinding) {
                Button(onClick = onOpenAdvice) { Text("정비 권고 보기") }
            }
        }

        if (!state.llmConfigured) {
            Text(
                "AI 설명 키가 설정되지 않아 사전 작성 문구로 안내합니다 " +
                    "(local.properties 의 llm.apiKey)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvironmentCard(
    state: DiagnosisUiState,
    onOverrideDaylight: (Daylight?) -> Unit,
    onOverrideWeather: (Weather?) -> Unit,
    onOverrideRoad: (RoadType?) -> Unit,
    onClearOverrides: () -> Unit,
) {
    val ctx = state.context
    SectionCard(
        title = "현재 주행 환경",
        trailing = {
            if (ctx.overridden) {
                Text(
                    "시연 모드",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EnvChip("시간대", ctx.daylight.label, ctx.isNight)
            EnvChip("날씨", ctx.weather.label, ctx.isWet)
            EnvChip("도로", ctx.roadType.label, ctx.isHighway)
            ctx.ambientTempC?.let { EnvChip("기온", "${it.toInt()}°C", ctx.isExtremeTemp) }
            ctx.speedKmh?.let { EnvChip("속도", "${it.toInt()} km/h") }
            if (state.driving.isDriving) EnvChip("주행 상태", "주행 중", true)
        }

        Text(
            "시연 전환 — 같은 고장이 환경에 따라 어떻게 다르게 판단되는지 즉석에서 보여줍니다",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoToggle("낮", ctx.daylight == Daylight.DAY && ctx.overridden) {
                onOverrideDaylight(Daylight.DAY)
            }
            DemoToggle("밤", ctx.daylight == Daylight.NIGHT && ctx.overridden) {
                onOverrideDaylight(Daylight.NIGHT)
            }
            DemoToggle("맑음", ctx.weather == Weather.CLEAR && ctx.overridden) {
                onOverrideWeather(Weather.CLEAR)
            }
            DemoToggle("비", ctx.weather == Weather.RAIN && ctx.overridden) {
                onOverrideWeather(Weather.RAIN)
            }
            DemoToggle("고속도로", ctx.roadType == RoadType.HIGHWAY && ctx.overridden) {
                onOverrideRoad(RoadType.HIGHWAY)
            }
            OutlinedButton(onClick = onClearOverrides) { Text("실측값으로") }
        }
    }
}

@Composable
private fun FindingsCard(
    state: DiagnosisUiState,
    onFindingClick: (Finding) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = "진단 결과 (${state.findings.size})", modifier = modifier) {
        if (state.findings.isEmpty()) {
            Text(
                "이상 없음",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.findings, key = { it.anomaly.id }) { finding ->
                FindingRow(
                    finding = finding,
                    summary = state.explanationFor(finding)?.summary,
                    selected = finding.anomaly.id == state.selectedAnomalyId,
                    onClick = { onFindingClick(finding) },
                )
            }
        }
    }
}

@Composable
private fun FindingRow(
    finding: Finding,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant,
        ),
        border = BorderStroke(2.dp, finding.adviceLevel.color().copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(finding.adviceLevel.color())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    "${finding.adviceLevel.level}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(finding.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary ?: finding.adviceLevel.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                "${finding.urgencyScore}점",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
