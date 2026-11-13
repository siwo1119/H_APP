package com.cardoctor.advisor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.domain.model.Daylight
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.domain.model.RoadType
import com.cardoctor.advisor.domain.model.Weather
import com.cardoctor.advisor.ui.DiagnosisUiState
import com.cardoctor.advisor.ui.car.CarDiagram
import com.cardoctor.advisor.ui.common.EmptyState
import com.cardoctor.advisor.ui.common.EnvChip
import com.cardoctor.advisor.ui.common.LevelBadge
import com.cardoctor.advisor.ui.common.SectionCard
import com.cardoctor.advisor.ui.common.SegmentedControl
import com.cardoctor.advisor.ui.common.StatusIcon
import com.cardoctor.advisor.ui.common.color
import com.cardoctor.advisor.ui.theme.StatusDanger
import com.cardoctor.advisor.ui.theme.StatusCaution
import com.cardoctor.advisor.ui.theme.StatusNormal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 홈 — 시연의 메인 화면.
 *
 * 좌측에 차량 도해, 우측에 종합 상태·환경·진단 목록을 둔다. 2560×1440 가로 화면을 전제로 한
 * 좌우 분할이며, 기획안의 "홈 화면 하나만 봐도 차 상태와 할 일이 보이게 한다"를 따른다.
 */
@Composable
fun HomeScreen(
    state: DiagnosisUiState,
    onPartClick: (CarPart) -> Unit,
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
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── 좌: 차량 도해 ──────────────────────────────────────────────────
        DiagramCard(state, onPartClick, Modifier.weight(0.44f).fillMaxHeight())

        // ── 우: 상태 + 환경 + 목록 ─────────────────────────────────────────
        Column(
            modifier = Modifier.weight(0.56f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
private fun DiagramCard(
    state: DiagnosisUiState,
    onPartClick: (CarPart) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "차량 부위 상태",
        subtitle = "부위를 누르면 상세 진단으로 이동합니다",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LegendDot(StatusNormal, "정상")
                LegendDot(StatusCaution, "주의")
                LegendDot(StatusDanger, "위험")
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            CarDiagram(
                statusOf = state::statusOf,
                selectedPart = state.selectedPart,
                onPartClick = onPartClick,
                modifier = Modifier.fillMaxSize().padding(20.dp),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "차량 앞쪽이 위",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverallStatusCard(
    state: DiagnosisUiState,
    onRunScan: () -> Unit,
    onOpenAdvice: () -> Unit,
) {
    val worst = state.worstLevel
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }

    val (headline, message) = when {
        !state.connected -> "연결 대기 중" to (state.connectionMessage ?: "차량에 연결되지 않았습니다")
        state.findings.isEmpty() -> "차량 상태 양호" to "점검한 항목에서 이상이 발견되지 않았습니다"
        else -> "이상 ${state.findings.size}건 감지" to worst!!.message
    }

    SectionCard(title = null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatusIcon(if (state.connected) worst else null, size = 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (worst != null) LevelBadge(worst, compact = true)
                }
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onRunScan, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp))
                Text("  전체 점검", style = MaterialTheme.typography.labelLarge)
            }
            if (state.findings.isNotEmpty()) {
                FilledTonalButton(onClick = onOpenAdvice) {
                    Text("정비 권고 보기", style = MaterialTheme.typography.labelLarge)
                }
            }
            Box(Modifier.weight(1f))
            if (state.lastScanAtMillis > 0) {
                Text(
                    "마지막 점검 ${formatter.format(Date(state.lastScanAtMillis))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.llmConfigured) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    "오프라인 설명 모드 — AI 키가 없어 사전 작성 문구로 안내합니다",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        subtitle = if (ctx.overridden) "시연 모드 — 수동으로 설정한 환경입니다" else "실측값 기준",
        trailing = {
            if (ctx.overridden) {
                TextButton(onClick = onClearOverrides) {
                    Text("실측값으로 되돌리기", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EnvChip("시간대", ctx.daylight.label.unknownAs(), ctx.isNight,
                icon = if (ctx.isNight) Icons.Rounded.DarkMode else Icons.Rounded.WbSunny)
            EnvChip("날씨", ctx.weather.label.unknownAs(), ctx.isWet, icon = Icons.Rounded.Cloud)
            EnvChip("도로", ctx.roadType.label.unknownAs(), ctx.isHighway, icon = Icons.Rounded.Route)
            ctx.ambientTempC?.let {
                EnvChip("기온", "${it.toInt()}°C", ctx.isExtremeTemp, icon = Icons.Rounded.Thermostat)
            }
            ctx.speedKmh?.let { EnvChip("속도", "${it.toInt()} km/h", icon = Icons.Rounded.Speed) }
            if (state.driving.isDriving) EnvChip("주행 상태", "주행 중", true, icon = Icons.Rounded.DirectionsCar)
        }

        // 시연 전환 — 같은 고장이 환경에 따라 어떻게 다르게 판단되는지 즉석에서 보여준다.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val o = ctx.overridden
            SegmentedControl(
                options = listOf("낮", "밤"),
                selectedIndex = if (!o) null else when (ctx.daylight) {
                    Daylight.DAY -> 0; Daylight.NIGHT -> 1; else -> null
                },
                onSelect = { onOverrideDaylight(if (it == 0) Daylight.DAY else Daylight.NIGHT) },
            )
            SegmentedControl(
                options = listOf("맑음", "비"),
                selectedIndex = if (!o) null else when (ctx.weather) {
                    Weather.CLEAR -> 0; Weather.RAIN -> 1; else -> null
                },
                onSelect = { onOverrideWeather(if (it == 0) Weather.CLEAR else Weather.RAIN) },
            )
            SegmentedControl(
                options = listOf("시내", "고속도로"),
                selectedIndex = if (!o) null else when (ctx.roadType) {
                    RoadType.CITY -> 0; RoadType.HIGHWAY -> 1; else -> null
                },
                onSelect = { onOverrideRoad(if (it == 0) RoadType.CITY else RoadType.HIGHWAY) },
            )
        }
    }
}

private fun String.unknownAs(): String = if (this == "—") "확인 중" else this

@Composable
private fun FindingsCard(
    state: DiagnosisUiState,
    onFindingClick: (Finding) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "진단 결과",
        modifier = modifier,
        trailing = {
            Text(
                "${state.findings.size}건",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        if (state.findings.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.VerifiedUser,
                title = "감지된 이상이 없습니다",
                message = "경고등이 켜지거나 신호가 어긋나면 이곳에 표시됩니다",
            )
            return@SectionCard
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.findings.sortedByDescending { it.adviceLevel.level }, key = { it.anomaly.id }) { finding ->
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
    val tint = finding.adviceLevel.color()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceVariant)
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 좌측 등급 색 띠
        Box(Modifier.width(6.dp).fillMaxHeight().background(tint))
        StatusIcon(finding.adviceLevel, size = 48.dp)
        Column(Modifier.weight(1f).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                finding.part.label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(finding.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary ?: finding.adviceLevel.message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LevelBadge(finding.adviceLevel)
        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}
