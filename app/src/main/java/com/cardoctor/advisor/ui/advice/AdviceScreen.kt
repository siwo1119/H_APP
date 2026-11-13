package com.cardoctor.advisor.ui.advice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.ui.DiagnosisUiState
import com.cardoctor.advisor.ui.common.EmptyState
import com.cardoctor.advisor.ui.common.LevelBadge
import com.cardoctor.advisor.ui.common.ScreenHeader
import com.cardoctor.advisor.ui.common.SectionCard
import com.cardoctor.advisor.ui.common.color
import com.cardoctor.advisor.ui.common.icon

/**
 * 정비 권고 화면. 3·4등급이 나오면 진입한다.
 *
 * 정비소 연결(아이디어 B)은 **내보낼 리포트를 화면에 만들어 두는 수준**까지 구현한다.
 * 실제 지도·예약 API 연동은 키와 제휴가 필요해 범위 밖이며, 그 자리를 비워 두기보다
 * 정비소에 그대로 전달 가능한 요약을 보여주는 편이 시연에서 더 설득력이 있다.
 */
@Composable
fun AdviceScreen(
    state: DiagnosisUiState,
    onExportReport: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = state.findings.sortedByDescending { it.adviceLevel.level }
    val worst = sorted.firstOrNull()?.adviceLevel

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenHeader(title = "정비 권고", subtitle = "가장 높은 등급을 기준으로 지금 해야 할 일을 안내합니다")

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.weight(1f)) {
            Column(
                Modifier.weight(0.42f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                HeroBanner(worst)
                LevelLadder(current = worst)
            }

            SectionCard(
                title = "정비소 전달용 요약",
                subtitle = "미리 전달하면 견적을 받고 과잉 정비를 줄일 수 있습니다",
                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                trailing = {
                    Button(
                        onClick = onExportReport,
                        enabled = sorted.isNotEmpty() && !state.reportExporting,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        if (state.reportExporting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Rounded.IosShare, null, Modifier.size(20.dp))
                        }
                        Text("  리포트 내보내기", style = MaterialTheme.typography.labelLarge)
                    }
                },
            ) {
                state.reportPath?.let { path ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(
                            "저장됨 · $path",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (sorted.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.CheckCircle,
                        title = "전달할 이상 항목이 없습니다",
                        message = "진단에서 이상이 감지되면 이곳에 요약이 만들어집니다",
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(sorted, key = { it.anomaly.id }) { ReportItem(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(worst: AdviceLevel?) {
    val tint = worst.color()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(tint)
            .padding(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(worst.icon(), null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (worst == null) "현재 상태" else "${worst.level}단계",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                worst?.title ?: "정비 필요 없음",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                worst?.message ?: "감지된 이상이 없어 별도의 정비가 필요하지 않습니다",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

/** 4단계 등급 사다리 — 지금 등급이 전체 중 어디인지 보여준다. */
@Composable
private fun LevelLadder(current: AdviceLevel?) {
    SectionCard(title = "권고 단계") {
        AdviceLevel.entries.sortedByDescending { it.level }.forEach { level ->
            val active = level == current
            val tint = level.color()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (active) tint.copy(alpha = 0.12f) else Color.Transparent)
                    .then(
                        if (active) Modifier.border(2.dp, tint, MaterialTheme.shapes.medium) else Modifier,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(if (active) tint else tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${level.level}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active) Color.White else tint,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(level.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        level.message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (active) {
                    Text("현재", style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReportItem(finding: Finding) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceVariant)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(finding.adviceLevel.color()))
        Column(
            Modifier.weight(1f).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(finding.part.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(finding.title, style = MaterialTheme.typography.titleMedium)
                }
                LevelBadge(finding.adviceLevel, compact = true)
            }
            Text(finding.anomaly.evidence, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Text(
                "감지 방식 ${finding.anomaly.pattern.label}  ·  긴급도 ${finding.urgencyScore}점",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
