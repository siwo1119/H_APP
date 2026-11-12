package com.cardoctor.advisor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 레퍼런스 디스플레이(12.8" 2560×1440 / 160dpi) 기준 테마.
 *
 * 160dpi 는 1dp = 1px 이다. 일반 휴대폰(≈420dpi) 감각으로 크기를 잡으면 화면에서 매우 작게
 * 보이므로, 본문 18sp / 제목 28sp 처럼 한 단계 크게 잡는다. 차량 환경에서 시인성이 우선이다.
 */

// 등급 색 — 기획안의 정상 회색 / 주의 노랑 / 위험 빨강
val StatusNormal = Color(0xFF7A8290)
val StatusCaution = Color(0xFFF2B01E)
val StatusDanger = Color(0xFFE23D3D)
val StatusCritical = Color(0xFFB71C1C)
val StatusOk = Color(0xFF2EA86A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF00213F),
    secondary = Color(0xFF9FC6F5),
    background = Color(0xFF0E1216),
    onBackground = Color(0xFFE6EAEF),
    surface = Color(0xFF171D23),
    onSurface = Color(0xFFE6EAEF),
    surfaceVariant = Color(0xFF232B33),
    onSurfaceVariant = Color(0xFFB7C0CA),
    error = StatusDanger,
    onError = Color.White,
    outline = Color(0xFF3A444F),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B63CE),
    background = Color(0xFFF4F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EBF0),
    error = StatusDanger,
)

private val IviTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp),
)

@Composable
fun CarDoctorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = IviTypography,
        content = content,
    )
}
