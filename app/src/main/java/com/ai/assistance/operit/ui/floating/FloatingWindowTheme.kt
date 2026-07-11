package com.ai.assistance.operit.ui.floating

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ColorScheme
import com.ai.assistance.operit.ui.theme.IosBlue
import com.ai.assistance.operit.ui.theme.IosGreen
import com.ai.assistance.operit.ui.theme.IosOrange
import com.ai.assistance.operit.ui.theme.IosRed
import com.ai.assistance.operit.ui.theme.IosLightSystemBackground
import com.ai.assistance.operit.ui.theme.IosLightPrimaryLabel
import com.ai.assistance.operit.ui.theme.IosLightSecondarySystemBackground
import com.ai.assistance.operit.ui.theme.IosLightSecondaryLabel
import com.ai.assistance.operit.ui.theme.IosLightSeparator

@Composable
fun FloatingWindowTheme(
    colorScheme: ColorScheme? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit
) {
    val finalColorScheme = colorScheme ?: lightColorScheme(
        primary = IosBlue,
        onPrimary = Color.White,
        primaryContainer = IosBlue.copy(alpha = 0.12f),
        onPrimaryContainer = IosBlue,
        secondary = IosGreen,
        onSecondary = Color.White,
        secondaryContainer = IosGreen.copy(alpha = 0.12f),
        onSecondaryContainer = IosGreen,
        tertiary = IosOrange,
        onTertiary = Color.White,
        tertiaryContainer = IosOrange.copy(alpha = 0.12f),
        onTertiaryContainer = IosOrange,
        error = IosRed,
        onError = Color.White,
        errorContainer = IosRed.copy(alpha = 0.12f),
        onErrorContainer = IosRed,
        background = IosLightSystemBackground,
        onBackground = IosLightPrimaryLabel,
        surface = IosLightSystemBackground,
        onSurface = IosLightPrimaryLabel,
        surfaceVariant = IosLightSecondarySystemBackground,
        onSurfaceVariant = IosLightSecondaryLabel,
        outline = IosLightSeparator
    )

    val defaultSmallTypography = Typography(
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.24).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            letterSpacing = (-0.08).sp
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.07.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.sp
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.24).sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.24).sp
        )
    )

    val finalTypography = typography ?: defaultSmallTypography

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = finalTypography,
        content = content
    )
}
