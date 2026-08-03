package org.cf0x.hma.helper.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Resolves the font family the current theme declares (android:fontFamily).
 * ROMs that apply custom/theme fonts (MIUI / HyperOS / ColorOS …) inject them
 * through this attribute; Compose's FontFamily.Default (Typeface.DEFAULT)
 * ignores that, so we read the resource and hand it to Material3 Typography.
 * Falls back to FontFamily.Default when the theme declares no font.
 */
fun systemFontFamily(context: Context): FontFamily {
    return runCatching {
        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.fontFamily))
        val resId = ta.getResourceId(0, 0)
        ta.recycle()
        if (resId != 0) FontFamily(Font(resId)) else FontFamily.Default
    }.getOrDefault(FontFamily.Default)
}

// MD3 Expressive typography — semi-bold headings with tighter tracking
fun expressiveTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
    val weight = FontWeight.SemiBold
    val tracking = (-0.02).sp

    return Typography(
        headlineLarge = TextStyle(
            fontFamily    = fontFamily,
            fontWeight    = weight,
            fontSize      = 32.sp,
            lineHeight    = 40.sp,
            letterSpacing = tracking
        ),
        headlineSmall = TextStyle(
            fontFamily    = fontFamily,
            fontWeight    = weight,
            fontSize      = 24.sp,
            lineHeight    = 32.sp,
            letterSpacing = tracking
        ),
        titleLarge = TextStyle(
            fontFamily    = fontFamily,
            fontWeight    = weight,
            fontSize      = 22.sp,
            lineHeight    = 28.sp,
            letterSpacing = tracking
        ),
        titleMedium = TextStyle(
            fontFamily    = fontFamily,
            fontWeight    = weight,
            fontSize      = 16.sp,
            lineHeight    = 24.sp,
            letterSpacing = tracking
        ),
        bodyLarge = TextStyle(
            fontFamily    = fontFamily,
            fontWeight    = FontWeight.Normal,
            fontSize      = 16.sp,
            lineHeight    = 24.sp,
            letterSpacing = 0.5.sp
        ),
        labelMedium = TextStyle(
            fontFamily    = fontFamily,
            fontWeight    = weight,
            fontSize      = 12.sp,
            lineHeight    = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}
