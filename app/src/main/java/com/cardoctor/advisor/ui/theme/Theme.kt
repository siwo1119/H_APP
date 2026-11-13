package com.cardoctor.advisor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 레퍼런스 디스플레이(12.8" 2560×1440 / 160dpi) 기준 테마.
 *
 * 160dpi 는 1dp = 1px 이다. 일반 휴대폰(≈420dpi) 감각으로 크기를 잡으면 화면에서 매우 작게
 * 보이므로, 본문 20sp / 제목 30sp 처럼 한 단계 크게 잡는다. 차량 환경에서 시인성이 우선이다.
 */

// 상태 색 — 정상 초록 / 1단계 파랑 / 2단계 주황 / 3단계 빨강 / 4단계 진홍
val StatusOk = Color(0xFF1E9E63)
val StatusInfo = Color(0xFF2F7DE1)
val StatusCaution = Color(0xFFE8A317)
val StatusDanger = Color(0xFFE5484D)
val StatusCritical = Color(0xFFB4232A)
val StatusNormal = Color(0xFF8A94A3)

private val Brand = Color(0xFF1F6FEB)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EDFD),
    onPrimaryContainer = Color(0xFF0B3B8C),
    secondary = Color(0xFF55657A),
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF151A21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A21),
    surfaceVariant = Color(0xFFF2F4F7),
    onSurfaceVariant = Color(0xFF677385),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFEEF1F5),
    outline = Color(0xFFD5DBE3),
    outlineVariant = Color(0xFFE6EAF0),
    error = StatusDanger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5B9BFF),
    onPrimary = Color(0xFF002A6B),
    primaryContainer = Color(0xFF16305A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFA9B6C8),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF1F2630),
    onSurfaceVariant = Color(0xFF9AA6B5),
    surfaceContainerLow = Color(0xFF1A2029),
    surfaceContainer = Color(0xFF212833),
    outline = Color(0xFF30394A),
    outlineVariant = Color(0xFF262E3B),
    error = StatusDanger,
    onError = Color.White,
)

private val IviTypography = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 29.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

private val IviShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

@Composable
fun CarDoctorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = IviTypography,
        shapes = IviShapes,
        content = content,
    )
}
