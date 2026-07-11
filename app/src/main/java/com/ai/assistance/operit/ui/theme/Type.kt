package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.net.toFile
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import java.io.File

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = -0.41.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = -0.32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = -0.41.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = -0.32.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = -0.24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = -0.41.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = -0.24.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = -0.08.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = -0.41.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.07.sp
    )
)

fun getSystemFontFamily(systemFontName: String): FontFamily {
    return when (systemFontName) {
        UserPreferencesManager.SYSTEM_FONT_SERIF -> FontFamily.Serif
        UserPreferencesManager.SYSTEM_FONT_SANS_SERIF -> FontFamily.SansSerif
        UserPreferencesManager.SYSTEM_FONT_MONOSPACE -> FontFamily.Monospace
        UserPreferencesManager.SYSTEM_FONT_CURSIVE -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}

fun loadCustomFontFamily(context: Context, fontPath: String): FontFamily? {
    return try {
        val file = if (fontPath.startsWith("file://")) {
            Uri.parse(fontPath).toFile()
        } else {
            File(fontPath)
        }

        if (!file.exists()) {
            AppLogger.e("TypeKt", "Font file does not exist: $fontPath")
            return null
        }

        FontFamily(
            Font(file)
        )
    } catch (e: Exception) {
        AppLogger.e("TypeKt", "Error loading custom font from $fontPath", e)
        null
    }
}

fun resolveConfiguredFontFamily(
    context: Context,
    useCustomFont: Boolean,
    fontType: String,
    systemFontName: String,
    customFontPath: String?,
): FontFamily? {
    if (!useCustomFont) {
        return null
    }

    return when (fontType) {
        UserPreferencesManager.FONT_TYPE_SYSTEM -> getSystemFontFamily(systemFontName)
        UserPreferencesManager.FONT_TYPE_FILE ->
            customFontPath
                ?.takeIf { it.isNotBlank() }
                ?.let { loadCustomFontFamily(context, it) }
        else -> null
    }
}

fun applyFontFamilyToTypography(
    baseTypography: Typography,
    fontFamily: FontFamily?,
): Typography {
    if (fontFamily == null) {
        return baseTypography
    }

    return Typography(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = fontFamily),
    )
}

fun createCustomTypography(
    context: Context,
    useCustomFont: Boolean,
    fontType: String,
    systemFontName: String,
    customFontPath: String?,
    fontScale: Float
): Typography {
    if (!useCustomFont && fontScale == 1.0f) {
        return Typography
    }

    val fontFamily =
        resolveConfiguredFontFamily(
            context = context,
            useCustomFont = useCustomFont,
            fontType = fontType,
            systemFontName = systemFontName,
            customFontPath = customFontPath,
        ) ?: FontFamily.Default
    val baseTypography = applyFontFamilyToTypography(Typography, fontFamily)

    fun TextStyle.withScale(): TextStyle = if (fontScale != 1.0f) {
        copy(fontSize = fontSize * fontScale, lineHeight = lineHeight * fontScale)
    } else {
        this
    }

    return Typography(
        displayLarge = baseTypography.displayLarge.withScale(),
        displayMedium = baseTypography.displayMedium.withScale(),
        displaySmall = baseTypography.displaySmall.withScale(),
        headlineLarge = baseTypography.headlineLarge.withScale(),
        headlineMedium = baseTypography.headlineMedium.withScale(),
        headlineSmall = baseTypography.headlineSmall.withScale(),
        titleLarge = baseTypography.titleLarge.withScale(),
        titleMedium = baseTypography.titleMedium.withScale(),
        titleSmall = baseTypography.titleSmall.withScale(),
        bodyLarge = baseTypography.bodyLarge.withScale(),
        bodyMedium = baseTypography.bodyMedium.withScale(),
        bodySmall = baseTypography.bodySmall.withScale(),
        labelLarge = baseTypography.labelLarge.withScale(),
        labelMedium = baseTypography.labelMedium.withScale(),
        labelSmall = baseTypography.labelSmall.withScale()
    )
}
