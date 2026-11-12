package com.cardoctor.advisor.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.data.local.HistoryStore
import com.cardoctor.advisor.ui.common.LevelBadge
import com.cardoctor.advisor.ui.common.SectionCard
import com.cardoctor.advisor.ui.common.color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 이력 — 날짜별 고장 기록과 **반복 고장** 표시.
 *
 * 같은 증상이 여러 번 기록됐다면 일회성 오류가 아니라는 신호이므로, 그 점을 눈에 띄게 표시한다.
 */
@Composable
fun HistoryScreen(
    records: List<HistoryStore.Record>,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repeatCounts = records.groupingBy { it.anomalyId }.eachCount()
    val formatter = remember { SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA) }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("진단 이력", style = MaterialTheme.typography.displaySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClear) { Text("이력 지우기") }
                OutlinedButton(onClick = onBack) { Text("홈으로") }
            }
        }

        SectionCard(title = "기록 (${records.size})", modifier = Modifier.weight(1f)) {
            if (records.isEmpty()) {
                Text(
                    "아직 기록된 진단 결과가 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@SectionCard
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records, key = { it.anomalyId + it.atMillis }) { record ->
                    val repeated = (repeatCounts[record.anomalyId] ?: 0) > 1
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = BorderStroke(1.dp, record.advice.color().copy(alpha = 0.7f)),
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
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(record.title, style = MaterialTheme.typography.titleMedium)
                                LevelBadge(record.advice, compact = true)
                            }
                            Text(
                                record.evidence,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    formatter.format(Date(record.atMillis)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (repeated) {
                                    Text(
                                        "반복 고장 (${repeatCounts[record.anomalyId]}회)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
