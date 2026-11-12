package com.wmdhs.shorea.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val ShoreShapes = Shapes()
private val ShoreTypography = Typography()

/**
 * Material 3 Expressive 主题：动态取色（Android 12+）、表现力动效方案、
 * 带 Emphasized 变体的字体层级。
 */
@Composable
internal fun ShoreATheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(dynamicColor, darkTheme, context) {
        when {
            dynamicColor && supportsDynamicColor ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            darkTheme -> ShoreDarkColors
            else -> ShoreLightColors
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ShoreShapes,
        typography = ShoreTypography,
        content = content,
    )
}
