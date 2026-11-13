package com.cardoctor.advisor.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.data.llm.LlmExplainer
import com.cardoctor.advisor.domain.model.Finding
import com.cardoctor.advisor.platform.DrivingStateMonitor
import com.cardoctor.advisor.ui.common.InfoRow
import com.cardoctor.advisor.ui.common.LevelBadge
import com.cardoctor.advisor.ui.common.SectionCard
import com.cardoctor.advisor.ui.common.StatusIcon
import com.cardoctor.advisor.ui.common.color

/**
 * 진단 상세.
 *
 * 기획안의 "권고 등급 근거(어떤 환경이 반영됐는지)" 표시가 이 화면의 핵심이다.
 * 규칙 기반이라 가능한 것이고, LLM 블랙박스와 대비되는 지점이다.
 *
 * 주행 중에는 상세 설명을 열지 않는다 — AAOS 가 강제하는 제약이자, 긴 글을 띄우면 차단된다.
 */
@Composable
fun DetailScreen(
    finding: Finding?,
    explanation: LlmExplainer.Explanation?,
    driving: DrivingStateMonitor.State,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (finding == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("선택된 진단 항목이 없습니다", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("홈으로") }
        }
        return
    }

    val tint = finding.adviceLevel.color()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── 헤더 ──
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로")
            }
            StatusIcon(finding.adviceLevel, size = 64.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    finding.part.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(finding.title, style = MaterialTheme.typography.headlineMedium)
            }
            LevelBadge(finding.adviceLevel)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // ── 지금 해야 할 일 ──
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(tint.copy(alpha = 0.10f))
                        .padding(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(width = 6.dp, height = 64.dp).clip(MaterialTheme.shapes.small).background(tint))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("지금 해야 할 일", style = MaterialTheme.typography.labelLarge, color = tint)
                        Text(finding.adviceLevel.message, style = MaterialTheme.typography.headlineSmall)
                    }
                }

                ExplanationCard(finding, explanation, driving)
            }

            Column(Modifier.weight(0.45f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                EvidenceCard(finding)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        "본 안내는 참고용입니다. 최종 판단은 정비 전문가에게 받으세요.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplanationCard(
    finding: Finding,
    explanation: LlmExplainer.Explanation?,
    driving: DrivingStateMonitor.State,
) {
    SectionCard(
        title = "설명",
        trailing = {
            val label = when {
                explanation == null -> null
                explanation.fromLlm -> "AI 생성"
                else -> "오프라인 문구"
            }
            label?.let {
                Row(
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) {
        when {
            driving.isDriving -> {
                Text(
                    driving.restrict(explanation?.summary ?: finding.adviceLevel.message),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "주행 중에는 요약만 표시됩니다. 정차 후 상세 설명을 확인하세요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            explanation == null -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text("설명을 생성하는 중입니다…", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> Text(explanation.detail, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 등급이 왜 이렇게 나왔는지를 그대로 보여준다 — "설명 가능한 판단". */
@Composable
private fun EvidenceCard(finding: Finding) {
    SectionCard(title = "판단 근거", subtitle = "규칙 기반으로 계산된 긴급도입니다") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledBlock("감지 방식", finding.anomaly.pattern.label)
            LabeledBlock("판단 신호", finding.anomaly.evidence)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("긴급도 계산", style = MaterialTheme.typography.titleSmall)
            InfoRow("기본 심각도 (${finding.part.label})", "+${finding.anomaly.baseSeverity.score}")
            finding.appliedWeights.forEach { w -> InfoRow(w.reason, "+${w.points}") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            InfoRow("합계", "${finding.urgencyScore}점", emphasize = true)
        }

        if (finding.floorApplied) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    "안전 핵심 부품이라 환경과 무관하게 최소 3단계로 판정됐습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (finding.appliedWeights.isEmpty() && !finding.floorApplied) {
            Text(
                "현재 환경에서 추가로 반영된 가중치는 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabeledBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
