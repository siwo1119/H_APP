package com.cardoctor.advisor.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.cardoctor.advisor.ui.common.LevelBadge
import com.cardoctor.advisor.ui.common.SectionCard

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(finding.part.label, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(finding.title, style = MaterialTheme.typography.displaySmall)
            }
            LevelBadge(finding.adviceLevel)
        }

        SectionCard(title = "지금 해야 할 일") {
            Text(finding.adviceLevel.message, style = MaterialTheme.typography.headlineSmall)
        }

        SectionCard(title = "AI 설명") {
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Text("설명을 생성하는 중입니다…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    Text(explanation.detail, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (explanation.fromLlm) "AI 생성 설명" else "오프라인 사전 작성 문구",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        EvidenceCard(finding)

        OutlinedButton(onClick = onBack) { Text("홈으로") }

        Text(
            "본 안내는 참고용입니다. 최종 판단은 정비 전문가에게 받으세요.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 등급이 왜 이렇게 나왔는지를 그대로 보여준다 — "설명 가능한 판단". */
@Composable
private fun EvidenceCard(finding: Finding) {
    SectionCard(title = "이 등급이 나온 근거") {
        LabeledRow("감지 방식", finding.anomaly.pattern.label)
        LabeledRow("판단 신호", finding.anomaly.evidence)

        Text("긴급도 계산", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp))

        ScoreRow(
            "기본 심각도 (${finding.part.label})",
            "+${finding.anomaly.baseSeverity.score}",
        )
        finding.appliedWeights.forEach { w ->
            ScoreRow(w.reason, "+${w.points}")
        }
        ScoreRow("합계", "${finding.urgencyScore}점", bold = true)

        if (finding.floorApplied) {
            Text(
                "안전 핵심 부품이라 환경과 무관하게 최소 3단계로 판정됐습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
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
private fun LabeledRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ScoreRow(label: String, value: String, bold: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
