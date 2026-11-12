package com.cardoctor.advisor.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.cardoctor.advisor.ui.theme.StatusCaution
import com.cardoctor.advisor.ui.theme.StatusCritical
import com.cardoctor.advisor.ui.theme.StatusDanger
import com.cardoctor.advisor.ui.theme.StatusOk

fun AdviceLevel?.color(): Color = when (this) {
    AdviceLevel.PULL_OVER -> StatusCritical
    AdviceLevel.IMMEDIATE_SERVICE -> StatusDanger
    AdviceLevel.AFTER_ARRIVAL -> StatusCaution
    AdviceLevel.SCHEDULED -> StatusCaution
    null -> StatusOk
}

/** 등급 배지 — "3단계 즉시 정비" 처럼 숫자와 이름을 함께 보여준다. */
@Composable
fun LevelBadge(
    level: AdviceLevel?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val bg = level.color()
    val text = if (level == null) {
        "이상 없음"
    } else if (compact) {
        "${level.level}단계"
    } else {
        "${level.level}단계 · ${level.title}"
    }
    Text(
        text = text,
        color = Color.White,
        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 5.dp else 9.dp),
    )
}

/** 현재 환경 칩 (날씨·낮밤·도로). */
@Composable
fun EnvChip(
    label: String,
    value: String,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) scheme.primary.copy(alpha = 0.22f) else scheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                trailing?.invoke()
            }
            content()
        }
    }
}

/** 시연용 환경 전환 토글. 선택 상태면 오버라이드가 걸린 것이다. */
@Composable
fun DemoToggle(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier,
    )
}
