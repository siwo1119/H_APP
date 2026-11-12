package com.cardoctor.advisor.ui.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.domain.detect.TelltaleCatalog
import com.cardoctor.advisor.ui.DiagnosisUiState
import com.cardoctor.advisor.ui.ScanPhase
import com.cardoctor.advisor.ui.ScanStep
import com.cardoctor.advisor.ui.common.SectionCard
import com.cardoctor.advisor.ui.theme.StatusCaution
import com.cardoctor.advisor.ui.theme.StatusOk

/** 자가진단 실행 — 기능별 점검 진행률. */
@Composable
fun ScanScreen(
    state: DiagnosisUiState,
    onRunScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("자가진단", style = MaterialTheme.typography.displaySmall)

        SectionCard(title = "전체 점검") {
            Text(
                when (state.scan.phase) {
                    ScanPhase.IDLE -> "점검을 시작하려면 아래 버튼을 누르세요."
                    ScanPhase.SCANNING -> "차량 신호를 읽는 중입니다…"
                    ScanPhase.DONE ->
                        "점검 완료 — ${state.findings.size}건의 이상이 감지됐습니다."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(
                progress = { if (state.scan.phase == ScanPhase.SCANNING) 0.35f else state.scan.ratio },
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRunScan) { Text("전체 점검 실행") }
                OutlinedButton(onClick = onBack) { Text("홈으로") }
            }
        }

        SectionCard(title = "점검 항목", modifier = Modifier.weight(1f)) {
            val steps = state.scan.steps.ifEmpty { emptyList() }
            if (steps.isEmpty()) {
                Text(
                    "아직 점검을 실행하지 않았습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(steps, key = { it.label }) { step -> StepCard(step) }
                }
            }
        }

        SectionCard(title = "진단 커버리지") {
            Text(
                "Vehicle SDK 상태 신호 기반으로 ${TelltaleCatalog.diagnosableCount}종의 경고등과 " +
                    "등화·와이퍼·타이어·오일 계통을 점검합니다. " +
                    "SDK 에 고장코드(DTC) API 가 없어, 상태 신호의 불일치·임계값·편차·추이에서 이상을 추론합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepCard(step: ScanStep) {
    val hasIssue = step.findingCount > 0
    val accent = if (hasIssue) StatusCaution else StatusOk
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(2.dp, if (step.done) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(step.label, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !step.done -> "점검 대기"
                    hasIssue -> "이상 ${step.findingCount}건"
                    else -> "정상"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (step.done) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
