package org.cf0x.hma.helper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// MD3 Expressive typography — semi-bold headings with tighter tracking
@Composable
fun expressiveTypography(): Typography {
    val weight = FontWeight.SemiBold
    val tracking = (-0.02).sp

    return Typography(
        headlineLarge = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 32.sp,
            lineHeight    = 40.sp,
            letterSpacing = tracking
        ),
        headlineSmall = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 24.sp,
            lineHeight    = 32.sp,
            letterSpacing = tracking
        ),
        titleLarge = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 22.sp,
            lineHeight    = 28.sp,
            letterSpacing = tracking
        ),
        titleMedium = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 16.sp,
            lineHeight    = 24.sp,
            letterSpacing = tracking
        ),
        bodyLarge = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = FontWeight.Normal,
            fontSize      = 16.sp,
            lineHeight    = 24.sp,
            letterSpacing = 0.5.sp
        ),
        labelMedium = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 12.sp,
            lineHeight    = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}
