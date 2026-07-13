package org.cf0x.hma.helper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import org.cf0x.hma.helper.data.ColorSource
import org.cf0x.hma.helper.data.ThemeMode

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun HMAHelperTheme(
    themeMode: ThemeMode     = ThemeMode.SYSTEM,
    colorSource: ColorSource = ColorSource.MONET,
    seedColor: Color         = Color(0xFF6750A4),
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark  = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
    }

    val effectiveSeed = remember(colorSource, isDark) {
        if (colorSource == ColorSource.MONET && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = if (isDark) dynamicDarkColorScheme(context)
            else        dynamicLightColorScheme(context)
            scheme.primary
        } else {
            null
        }
    } ?: seedColor

    DynamicMaterialTheme(
        seedColor    = effectiveSeed,
        isDark       = isDark,
        animate      = true,
        style        = paletteStyle,
        typography   = expressiveTypography(),
        shapes       = Shapes
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            content = content
        )
    }
}
