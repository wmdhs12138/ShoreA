package com.wmdhs.shorea

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2546A8), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF001452),
    secondary = Color(0xFF006A6A), onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF1EF), onSecondaryContainer = Color(0xFF002020),
    tertiary = Color(0xFF9C4311), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCA), onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF191B22),
    surface = Color(0xFFF9F9FF), onSurface = Color(0xFF191B22),
    surfaceVariant = Color(0xFFE2E2EC), onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF2546A8), surfaceBright = Color(0xFFF9F9FF), surfaceDim = Color(0xFFD9D9E2),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF3F3FB),
    surfaceContainer = Color(0xFFEDEDF5), surfaceContainerHigh = Color(0xFFE7E7EF), surfaceContainerHighest = Color(0xFFE2E2EA),
    outline = Color(0xFF767680), outlineVariant = Color(0xFFC6C6D0),
    inverseSurface = Color(0xFF2E3037), inverseOnSurface = Color(0xFFF0F0F8), inversePrimary = Color(0xFFB8C3FF),
    scrim = Color(0xFF000000), error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C3FF), onPrimary = Color(0xFF002582),
    primaryContainer = Color(0xFF17318F), onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF80D5D3), onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF005050), onSecondaryContainer = Color(0xFF9CF1EF),
    tertiary = Color(0xFFFFB690), onTertiary = Color(0xFF572000),
    tertiaryContainer = Color(0xFF7C2D00), onTertiaryContainer = Color(0xFFFFDBCA),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF45464F), onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB8C3FF), surfaceBright = Color(0xFF383A42), surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191B22),
    surfaceContainer = Color(0xFF1D1F26), surfaceContainerHigh = Color(0xFF282A31), surfaceContainerHighest = Color(0xFF33353C),
    outline = Color(0xFF90909A), outlineVariant = Color(0xFF45464F),
    inverseSurface = Color(0xFFE2E2E9), inverseOnSurface = Color(0xFF2E3037), inversePrimary = Color(0xFF2546A8),
    scrim = Color(0xFF000000), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)
private val ShoreShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)
private val BaseTypography = Typography()
private val ShoreTypography = Typography(
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
internal fun ShoreATheme(content: @Composable () -> Unit) {
    ShoreAExpressiveThemeBridge(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = ShoreShapes,
        typography = ShoreTypography,
        content = content,
    )
}
