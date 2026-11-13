package com.cardoctor.advisor.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.data.local.HistoryStore
import com.cardoctor.advisor.ui.common.EmptyState
import com.cardoctor.advisor.ui.common.LevelBadge
import com.cardoctor.advisor.ui.common.ScreenHeader
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
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repeatCounts = records.groupingBy { it.anomalyId }.eachCount()
    val dateFmt = remember { SimpleDateFormat("M월 d일", Locale.KOREA) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenHeader(
            title = "진단 이력",
            subtitle = "같은 증상이 반복되면 일회성 오류가 아니라는 신호입니다",
        ) {
            if (records.isNotEmpty()) {
                OutlinedButton(onClick = onClear) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp))
                    Text("  이력 지우기", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        SectionCard(
            title = "기록",
            modifier = Modifier.weight(1f),
            trailing = {
                Text(
                    "${records.size}건",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            if (records.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "아직 기록된 진단 결과가 없습니다",
                    message = "이상이 감지되면 날짜별로 이곳에 쌓입니다",
                )
                return@SectionCard
            }
            LazyColumn {
                items(records, key = { it.anomalyId + it.atMillis }) { record ->
                    val count = repeatCounts[record.anomalyId] ?: 0
                    TimelineItem(
                        record = record,
                        date = dateFmt.format(Date(record.atMillis)),
                        time = timeFmt.format(Date(record.atMillis)),
                        repeatCount = count,
                        isLast = record == records.last(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(
    record: HistoryStore.Record,
    date: String,
    time: String,
    repeatCount: Int,
    isLast: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = record.advice.color()
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 날짜·시간
        Column(Modifier.width(96.dp).padding(top = 18.dp), horizontalAlignment = Alignment.End) {
            Text(date, style = MaterialTheme.typography.labelLarge)
            Text(time, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        // 타임라인 점·선
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!isLast) {
                Box(Modifier.padding(top = 24.dp).width(2.dp).fillMaxHeight().background(scheme.outline))
            }
            Box(
                Modifier
                    .padding(top = 22.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
        // 내용
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = 14.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(scheme.surfaceVariant)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(record.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (repeatCount > 1) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(scheme.error.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Repeat, null, tint = scheme.error, modifier = Modifier.size(16.dp))
                        Text(
                            "반복 ${repeatCount}회",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                LevelBadge(record.advice, compact = true)
            }
            Text(record.evidence, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}
