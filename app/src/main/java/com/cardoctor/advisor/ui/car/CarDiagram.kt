package com.cardoctor.advisor.ui.car

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.ui.PartStatus
import com.cardoctor.advisor.ui.theme.StatusCaution
import com.cardoctor.advisor.ui.theme.StatusDanger

/**
 * 차량 부위 도해 — 탑다운 뷰.
 *
 * 기획안의 "3D 차량 모델 위에서 문제 부위를 색으로 강조하고, 누르면 상세로 진입" 요구를
 * 충족하는 뷰다. 부위별 색 표시(회색/노랑/빨강)와 터치 히트테스트가 실제로 동작한다.
 *
 * ## 왜 3D 가 아닌가
 * Filament/SceneView 경로는 **부위별 메시가 분리된 차량 glTF 에셋**을 전제한다. 그 에셋이
 * 확보되기 전까지 3D 뷰어를 붙이면 빈 화면이 되므로, 동작하는 2D 도해를 먼저 둔다.
 *
 * 교체 지점은 이 파일 하나다 — [CarPartLayout] 이 부위↔영역 매핑을 들고 있고, 화면들은
 * `(CarPart) -> Unit` 콜백과 [PartStatus] 만 알고 있으므로, 3D 뷰로 바꿔도 호출부는 그대로다.
 */
@Composable
fun CarDiagram(
    statusOf: (CarPart) -> PartStatus,
    selectedPart: CarPart?,
    onPartClick: (CarPart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    val pulse = rememberInfiniteTransition(label = "danger-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "danger-pulse-alpha",
    )

    // 호스트 IVI 가 라이트/다크 어느 쪽이든 읽히도록 테마 색에서 파생시킨다.
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme) {
        DiagramPalette(
            body = scheme.surface,
            bodyEdge = scheme.outline,
            glass = scheme.primaryContainer,
            partNormal = scheme.onSurfaceVariant.copy(alpha = 0.14f),
            partEdge = scheme.onSurfaceVariant.copy(alpha = 0.22f),
            label = scheme.onSurface,
            labelBg = scheme.surface,
            selection = scheme.primary,
        )
    }

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 차량 비율(세로로 긴 탑다운)을 유지한 채 가운데 맞춤
        val layout = remember(widthPx, heightPx) { CarPartLayout.fit(widthPx, heightPx) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    detectTapGestures { offset ->
                        layout.hitTest(offset)?.let(onPartClick)
                    }
                }
        ) {
            drawCarBody(layout, palette)
            layout.parts.forEach { (part, rect) ->
                // 차체는 배경으로 이미 그렸으므로 부품으로 다시 칠하지 않는다.
                if (part == CarPart.BODY) return@forEach
                drawPart(
                    part = part,
                    rect = rect,
                    status = statusOf(part),
                    selected = part == selectedPart,
                    pulseAlpha = pulseAlpha,
                    palette = palette,
                    textMeasurer = textMeasurer,
                )
            }
        }
    }
}

/** 다이어그램이 쓰는 색 묶음. 테마에서 파생되므로 라이트/다크 모두에서 대비가 유지된다. */
private data class DiagramPalette(
    val body: Color,
    val bodyEdge: Color,
    val glass: Color,
    val partNormal: Color,
    val partEdge: Color,
    val label: Color,
    val labelBg: Color,
    val selection: Color,
)

private fun DrawScope.drawCarBody(layout: CarPartLayout, palette: DiagramPalette) {
    val body = layout.bodyRect

    // 차체 윤곽 — 앞코를 좁혀 "위가 앞"임을 알아보게 한다.
    val outline = Path().apply {
        val noseInset = body.width * 0.12f
        val noseDepth = body.height * 0.07f
        val tailInset = body.width * 0.05f
        moveTo(body.left + noseInset, body.top)
        lineTo(body.right - noseInset, body.top)
        lineTo(body.right, body.top + noseDepth)
        lineTo(body.right, body.bottom - noseDepth * 0.6f)
        lineTo(body.right - tailInset, body.bottom)
        lineTo(body.left + tailInset, body.bottom)
        lineTo(body.left, body.bottom - noseDepth * 0.6f)
        lineTo(body.left, body.top + noseDepth)
        close()
    }
    // 은은한 그림자 — 차체가 배경 위에 떠 보이게
    for (i in 3 downTo 1) {
        translate(top = layout.scale(0.004f) * i) {
            drawPath(outline, color = Color.Black.copy(alpha = 0.025f))
        }
    }
    drawPath(outline, color = palette.body)
    drawPath(outline, color = palette.bodyEdge, style = Stroke(width = layout.scale(0.004f)))

    // 앞/뒤 유리 — 사다리꼴로 그려 방향감을 준다.
    fun glass(topY: Float, bottomY: Float, topInset: Float, bottomInset: Float) {
        val p = Path().apply {
            moveTo(body.left + body.width * topInset, body.top + body.height * topY)
            lineTo(body.right - body.width * topInset, body.top + body.height * topY)
            lineTo(body.right - body.width * bottomInset, body.top + body.height * bottomY)
            lineTo(body.left + body.width * bottomInset, body.top + body.height * bottomY)
            close()
        }
        drawPath(p, color = palette.glass.copy(alpha = 0.9f))
    }
    glass(topY = 0.235f, bottomY = 0.305f, topInset = 0.16f, bottomInset = 0.06f)
    glass(topY = 0.855f, bottomY = 0.785f, topInset = 0.16f, bottomInset = 0.06f)

    // 중심선 — 좌우 구분
    val cx = body.center.x
    drawLine(
        color = palette.bodyEdge.copy(alpha = 0.35f),
        start = Offset(cx, body.top + layout.scale(0.03f)),
        end = Offset(cx, body.bottom - layout.scale(0.03f)),
        strokeWidth = layout.scale(0.0015f),
    )
}

private fun DrawScope.drawPart(
    part: CarPart,
    rect: Rect,
    status: PartStatus,
    selected: Boolean,
    pulseAlpha: Float,
    palette: DiagramPalette,
    textMeasurer: TextMeasurer,
) {
    val fill = when (status) {
        PartStatus.NORMAL -> palette.partNormal
        PartStatus.CAUTION -> StatusCaution
        PartStatus.DANGER -> StatusDanger.copy(alpha = pulseAlpha)
    }
    val radius = CornerRadius(rect.minDimension * 0.25f)

    drawRoundRect(
        color = fill,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = radius,
    )
    drawRoundRect(
        color = if (status == PartStatus.NORMAL) palette.partEdge else fill,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = radius,
        style = Stroke(width = 2f),
    )

    if (selected) {
        val pad = rect.minDimension * 0.12f
        drawRoundRect(
            color = palette.selection,
            topLeft = Offset(rect.left - pad, rect.top - pad),
            size = Size(rect.width + pad * 2, rect.height + pad * 2),
            cornerRadius = CornerRadius(radius.x + pad),
            style = Stroke(width = 3f),
        )
    }

    // 이상이 있는 부위만 이름을 띄운다 — 정상 부위까지 라벨을 깔면 화면이 읽히지 않는다.
    if (status != PartStatus.NORMAL) {
        val measured = textMeasurer.measure(
            text = part.label,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.label),
        )
        val padX = 10f
        val padY = 5f
        val w = measured.size.width + padX * 2
        val h = measured.size.height + padY * 2
        val bx = (rect.center.x - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
        val by = (rect.bottom + 8f).coerceAtMost((size.height - h).coerceAtLeast(0f))
        val accent = if (status == PartStatus.DANGER) StatusDanger else StatusCaution
        drawRoundRect(
            color = palette.labelBg,
            topLeft = Offset(bx, by),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2f),
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(bx, by),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2f),
            style = Stroke(width = 2f),
        )
        drawText(measured, topLeft = Offset(bx + padX, by + padY))
    }
}

/**
 * 부위 ↔ 화면 영역 매핑. 정규화 좌표(0..1)로 정의하고 실제 크기에 맞춰 환산한다.
 *
 * 좌표계는 **차량 전방이 위쪽**인 탑다운 뷰다.
 */
class CarPartLayout private constructor(
    private val originX: Float,
    private val originY: Float,
    private val side: Float,
) {
    val bodyRect: Rect get() = rel(0.26f, 0.05f, 0.74f, 0.95f)

    /** 히트테스트 우선순위 순서 — 앞쪽 항목이 먼저 잡힌다. 작은 부품이 차체보다 앞에 온다. */
    val parts: List<Pair<CarPart, Rect>> by lazy {
        listOf(
            CarPart.HEADLAMP_LEFT to rel(0.275f, 0.055f, 0.40f, 0.115f),
            CarPart.FOG_LAMP to rel(0.435f, 0.055f, 0.565f, 0.10f),
            CarPart.HEADLAMP_RIGHT to rel(0.60f, 0.055f, 0.725f, 0.115f),
            CarPart.ENGINE to rel(0.315f, 0.135f, 0.685f, 0.225f),
            CarPart.WASHER to rel(0.285f, 0.245f, 0.325f, 0.285f),
            CarPart.WIPER to rel(0.335f, 0.245f, 0.665f, 0.285f),
            CarPart.TIRE_FL to rel(0.155f, 0.175f, 0.25f, 0.33f),
            CarPart.TIRE_FR to rel(0.75f, 0.175f, 0.845f, 0.33f),
            CarPart.TIRE_RL to rel(0.155f, 0.655f, 0.25f, 0.81f),
            CarPart.TIRE_RR to rel(0.75f, 0.655f, 0.845f, 0.81f),
            CarPart.STEERING to rel(0.335f, 0.375f, 0.455f, 0.455f),
            CarPart.AIRBAG to rel(0.335f, 0.475f, 0.455f, 0.535f),
            CarPart.SEATBELT to rel(0.545f, 0.375f, 0.665f, 0.455f),
            CarPart.BATTERY to rel(0.545f, 0.475f, 0.665f, 0.555f),
            CarPart.TRANSMISSION to rel(0.425f, 0.575f, 0.575f, 0.645f),
            CarPart.BRAKE to rel(0.395f, 0.665f, 0.605f, 0.735f),
            CarPart.DOOR to rel(0.262f, 0.375f, 0.295f, 0.645f),
            CarPart.TAIL_LAMP to rel(0.30f, 0.885f, 0.70f, 0.935f),
            CarPart.BODY to rel(0.26f, 0.05f, 0.74f, 0.95f),
        )
    }

    fun hitTest(point: Offset): CarPart? =
        parts.firstOrNull { (_, rect) -> rect.contains(point) }?.first

    fun rel(left: Float, top: Float, right: Float, bottom: Float): Rect = Rect(
        left = originX + left * side,
        top = originY + top * side,
        right = originX + right * side,
        bottom = originY + bottom * side,
    )

    fun scale(v: Float): Float = v * side

    fun corner(v: Float) = CornerRadius(v * side)

    companion object {
        fun fit(widthPx: Float, heightPx: Float): CarPartLayout {
            // 차량(타이어 포함)이 실제로 차지하는 영역은 정규화 좌표 기준 가로 ≈0.69, 세로 ≈0.90 이다.
            // 정사각형 전체가 아니라 이 영역을 기준으로 맞춰야 카드 공간을 낭비하지 않는다.
            val side = minOf(widthPx / 0.74f, heightPx / 0.94f)
            return CarPartLayout(
                originX = widthPx / 2f - side * 0.5f,
                originY = heightPx / 2f - side * 0.5f,
                side = side,
            )
        }
    }
}
