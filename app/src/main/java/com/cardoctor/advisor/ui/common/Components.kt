package com.cardoctor.advisor.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardoctor.advisor.domain.model.AdviceLevel
import com.cardoctor.advisor.ui.theme.StatusCaution
import com.cardoctor.advisor.ui.theme.StatusCritical
import com.cardoctor.advisor.ui.theme.StatusDanger
import com.cardoctor.advisor.ui.theme.StatusInfo
import com.cardoctor.advisor.ui.theme.StatusOk

/** 등급별 색 — null(이상 없음)은 초록. 1~4 단계로 갈수록 파랑 → 주황 → 빨강 → 진홍. */
fun AdviceLevel?.color(): Color = when (this) {
    AdviceLevel.PULL_OVER -> StatusCritical
    AdviceLevel.IMMEDIATE_SERVICE -> StatusDanger
    AdviceLevel.AFTER_ARRIVAL -> StatusCaution
    AdviceLevel.SCHEDULED -> StatusInfo
    null -> StatusOk
}

fun AdviceLevel?.icon(): ImageVector = when (this) {
    AdviceLevel.PULL_OVER, AdviceLevel.IMMEDIATE_SERVICE -> Icons.Rounded.Error
    AdviceLevel.AFTER_ARRIVAL -> Icons.Rounded.Warning
    AdviceLevel.SCHEDULED -> Icons.Rounded.Info
    null -> Icons.Rounded.CheckCircle
}

/** 등급 배지 — 옅은 배경 + 색 글자의 톤 배지. "3단계 · 즉시 정비" 처럼 숫자와 이름을 함께 보여준다. */
@Composable
fun LevelBadge(
    level: AdviceLevel?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val tint = level.color()
    val text = when {
        level == null -> "이상 없음"
        compact -> "${level.level}단계"
        else -> "${level.level}단계 · ${level.title}"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.13f))
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 5.dp else 8.dp),
    ) {
        Box(Modifier.size(if (compact) 8.dp else 10.dp).clip(CircleShape).background(tint))
        Text(
            text = text,
            color = tint,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 등급 아이콘을 원형 톤 배경에 담은 것. 상태 요약·목록 앞머리에 쓴다. */
@Composable
fun StatusIcon(level: AdviceLevel?, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val tint = level.color()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(level.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** 화면 상단 제목 영역. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** 기본 카드 — 흰 면 + 아주 옅은 테두리. 그림자 대신 배경과의 명도 차로 구분한다. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (title != null || trailing != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** 현재 환경 타일 (시간대·날씨·도로 …). 강조 상태면 브랜드 톤으로 칠한다. */
@Composable
fun EnvChip(
    label: String,
    value: String,
    highlighted: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (highlighted) scheme.primaryContainer else scheme.surfaceVariant
    val fg = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        icon?.let { Icon(it, null, tint = if (highlighted) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 세그먼트 컨트롤 — 연관된 선택지를 한 덩어리로 묶는다.
 * selectedIndex 가 null 이면 아무것도 선택되지 않은 상태(= 실측값 사용).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val bg by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "seg-bg")
            val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, label = "seg-fg")
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

/** 시연용 환경 전환 토글 (단일). 새 화면은 [SegmentedControl] 을 쓴다. */
@Composable
fun DemoToggle(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SegmentedControl(listOf(label), if (selected) 0 else null, { onClick() }, modifier)
}

/** 라벨-값 한 줄. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** 빈 상태 안내. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
