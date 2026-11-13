package com.cardoctor.advisor.ui.scan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.domain.detect.TelltaleCatalog
import com.cardoctor.advisor.ui.DiagnosisUiState
import com.cardoctor.advisor.ui.ScanPhase
import com.cardoctor.advisor.ui.ScanStep
import com.cardoctor.advisor.ui.common.EmptyState
import com.cardoctor.advisor.ui.common.ScreenHeader
import com.cardoctor.advisor.ui.common.SectionCard
import com.cardoctor.advisor.ui.theme.StatusCaution
import com.cardoctor.advisor.ui.theme.StatusOk

/** 자가진단 실행 — 기능별 점검 진행률. */
@Composable
fun ScanScreen(
    state: DiagnosisUiState,
    onRunScan: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scanning = state.scan.phase == ScanPhase.SCANNING
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenHeader(
            title = "자가진단",
            subtitle = "차량 신호를 계통별로 읽어 이상 여부를 확인합니다",
        ) {
            Button(
                onClick = onRunScan,
                enabled = !scanning,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp))
                Text(if (scanning) "  점검 중…" else "  전체 점검 실행", style = MaterialTheme.typography.labelLarge)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.weight(1f)) {
            // ── 진행 요약 ──
            SectionCard(title = null, modifier = Modifier.weight(0.34f)) {
                ProgressRing(state)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(18.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "경고등 ${TelltaleCatalog.diagnosableCount}종과 등화·와이퍼·타이어·오일 계통을 점검합니다. " +
                                "고장코드(DTC) 없이 상태 신호의 불일치·임계값·편차·추이로 이상을 추론합니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── 항목별 결과 ──
            SectionCard(
                title = "점검 항목",
                modifier = Modifier.weight(0.66f),
                trailing = {
                    if (state.scan.total > 0) {
                        Text(
                            "${state.scan.completed} / ${state.scan.total}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ) {
                if (state.scan.steps.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.TaskAlt,
                        title = "아직 점검을 실행하지 않았습니다",
                        message = "오른쪽 위 ‘전체 점검 실행’을 눌러 시작하세요",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.scan.steps, key = { it.label }) { step -> StepCard(step) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(state: DiagnosisUiState) {
    val phase = state.scan.phase
    val target = when (phase) {
        ScanPhase.IDLE -> 0f
        ScanPhase.SCANNING -> state.scan.ratio.coerceAtLeast(0.05f)
        ScanPhase.DONE -> 1f
    }
    val progress by animateFloatAsState(target, label = "scan-progress")
    val issues = state.findings.size
    val ringColor = when {
        phase != ScanPhase.DONE -> MaterialTheme.colorScheme.primary
        issues > 0 -> StatusCaution
        else -> StatusOk
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(220.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 16.dp,
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(220.dp),
                color = ringColor,
                strokeWidth = 16.dp,
                strokeCap = StrokeCap.Round,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (phase) {
                        ScanPhase.IDLE -> "대기"
                        ScanPhase.SCANNING -> "점검 중"
                        ScanPhase.DONE -> "완료"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            when (phase) {
                ScanPhase.IDLE -> "점검을 시작하면 진행률이 표시됩니다"
                ScanPhase.SCANNING -> "차량 신호를 읽는 중입니다…"
                ScanPhase.DONE -> if (issues == 0) "모든 항목이 정상입니다" else "${issues}건의 이상이 감지됐습니다"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun StepCard(step: ScanStep) {
    val hasIssue = step.findingCount > 0
    val scheme = MaterialTheme.colorScheme
    val (icon, tint, status) = when {
        !step.done -> Triple(Icons.Rounded.HourglassEmpty, scheme.onSurfaceVariant, "점검 대기")
        hasIssue -> Triple(Icons.Rounded.Warning, StatusCaution, "이상 ${step.findingCount}건")
        else -> Triple(Icons.Rounded.CheckCircle, StatusOk, "정상")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(step.label, style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}
