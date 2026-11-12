package com.cardoctor.advisor.ui.advice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
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
import com.cardoctor.advisor.ui.DiagnosisUiState
import com.cardoctor.advisor.ui.common.LevelBadge
import com.cardoctor.advisor.ui.common.SectionCard
import com.cardoctor.advisor.ui.common.color

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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = state.findings.sortedByDescending { it.adviceLevel.level }
    val worst = sorted.firstOrNull()?.adviceLevel

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("정비 권고", style = MaterialTheme.typography.displaySmall)
            LevelBadge(worst)
        }

        if (worst != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(worst.color())
                    .padding(28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        worst.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        worst.message,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                }
            }
        }

        LevelGuideRow()

        SectionCard(
            title = "정비소 전달용 요약",
            modifier = Modifier.weight(1f),
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.reportExporting) {
                        Text(
                            "내보내는 중…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onExportReport, enabled = sorted.isNotEmpty()) {
                        Text("리포트 내보내기")
                    }
                }
            },
        ) {
            if (sorted.isEmpty()) {
                Text("전달할 이상 항목이 없습니다.", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    "아래 내용을 정비소에 미리 전달하면 견적을 받고 과잉 정비를 줄일 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.reportPath?.let { path ->
                    Text(
                        "저장됨 · $path",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sorted, key = { it.anomaly.id }) { finding ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            border = BorderStroke(1.dp, finding.adviceLevel.color()),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "${finding.part.label} · ${finding.title}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    LevelBadge(finding.adviceLevel, compact = true)
                                }
                                Text(
                                    finding.anomaly.evidence,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "감지 방식: ${finding.anomaly.pattern.label} · 긴급도 ${finding.urgencyScore}점",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onBack) { Text("홈으로") }
    }
}

/** 4단계 등급 카드 — 지금 등급이 전체 중 어디인지 보여준다. */
@Composable
private fun LevelGuideRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        AdviceLevel.entries.sortedByDescending { it.level }.forEach { level ->
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                border = BorderStroke(2.dp, level.color().copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${level.level}단계 ${level.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = level.color(),
                    )
                    Text(
                        level.message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
