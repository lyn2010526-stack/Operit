package com.cynosure.operit.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Build
import com.cynosure.operit.util.AppLogger
import com.cynosure.operit.R
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.cynosure.operit.data.preferences.UserPreferencesManager
import com.cynosure.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_AUTO
import com.cynosure.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_DARK
import com.cynosure.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_LIGHT
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

private val IosLightColorScheme =
    lightColorScheme(
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
        outline = IosLightSeparator,
        outlineVariant = IosLightOpaqueSeparator,
        inverseSurface = IosDarkSystemBackground,
        inverseOnSurface = IosDarkPrimaryLabel,
        inversePrimary = IosBlue.copy(alpha = 0.7f),
        surfaceTint = IosBlue.copy(alpha = 0.08f),
    )

private val IosDarkColorScheme =
    darkColorScheme(
        primary = IosBlue,
        onPrimary = Color.White,
        primaryContainer = IosBlue.copy(alpha = 0.16f),
        onPrimaryContainer = Color(0xFFA8D4FF),
        secondary = IosGreen,
        onSecondary = Color.White,
        secondaryContainer = IosGreen.copy(alpha = 0.16f),
        onSecondaryContainer = Color(0xFFA8F0B0),
        tertiary = IosOrange,
        onTertiary = Color.White,
        tertiaryContainer = IosOrange.copy(alpha = 0.16f),
        onTertiaryContainer = Color(0xFFFFD6A0),
        error = IosRed,
        onError = Color.White,
        errorContainer = IosRed.copy(alpha = 0.16f),
        onErrorContainer = Color(0xFFFFB4AB),
        background = IosDarkSystemBackground,
        onBackground = IosDarkPrimaryLabel,
        surface = IosDarkSystemBackground,
        onSurface = IosDarkPrimaryLabel,
        surfaceVariant = IosDarkSecondarySystemBackground,
        onSurfaceVariant = IosDarkSecondaryLabel,
        outline = IosDarkSeparator,
        outlineVariant = IosDarkOpaqueSeparator,
        inverseSurface = IosLightSystemBackground,
        inverseOnSurface = IosLightPrimaryLabel,
        inversePrimary = IosBlue.copy(alpha = 0.7f),
        surfaceTint = IosBlue.copy(alpha = 0.12f),
    )

private val DarkColorScheme = IosDarkColorScheme
private val LightColorScheme = IosLightColorScheme


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val useSystemTheme by preferencesManager.useSystemTheme.collectAsState(initial = true)
    val themeMode by
        preferencesManager.themeMode.collectAsState(
            initial = UserPreferencesManager.THEME_MODE_LIGHT
        )
    val useCustomColors by preferencesManager.useCustomColors.collectAsState(initial = false)
    val customPrimaryColor by preferencesManager.customPrimaryColor.collectAsState(initial = null)
    val customSecondaryColor by
        preferencesManager.customSecondaryColor.collectAsState(initial = null)
    val onColorMode by preferencesManager.onColorMode.collectAsState(initial = ON_COLOR_MODE_AUTO)

    val useBackgroundImage by preferencesManager.useBackgroundImage.collectAsState(initial = false)
    val backgroundImageUri by preferencesManager.backgroundImageUri.collectAsState(initial = null)
    val backgroundImageOpacity by
        preferencesManager.backgroundImageOpacity.collectAsState(initial = 0.3f)

    val backgroundMediaType by
        preferencesManager.backgroundMediaType.collectAsState(
            initial = UserPreferencesManager.MEDIA_TYPE_IMAGE
        )
    val videoBackgroundMuted by
        preferencesManager.videoBackgroundMuted.collectAsState(initial = true)
    val videoBackgroundLoop by preferencesManager.videoBackgroundLoop.collectAsState(initial = true)

    val useCustomStatusBarColor by
        preferencesManager.useCustomStatusBarColor.collectAsState(initial = false)
    val customStatusBarColorValue by
        preferencesManager.customStatusBarColor.collectAsState(initial = null)
    val statusBarTransparent by
        preferencesManager.statusBarTransparent.collectAsState(initial = false)
    val statusBarHidden by
        preferencesManager.statusBarHidden.collectAsState(initial = false)

    val useBackgroundBlur by preferencesManager.useBackgroundBlur.collectAsState(initial = false)
    val backgroundBlurRadius by preferencesManager.backgroundBlurRadius.collectAsState(initial = 10f)

    val useCustomFont by preferencesManager.useCustomFont.collectAsState(initial = false)
    val fontType by preferencesManager.fontType.collectAsState(initial = UserPreferencesManager.FONT_TYPE_SYSTEM)
    val systemFontName by preferencesManager.systemFontName.collectAsState(initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT)
    val customFontPath by preferencesManager.customFontPath.collectAsState(initial = null)
    val fontScale by preferencesManager.fontScale.collectAsState(initial = 1.0f)

    val customTypography = remember(useCustomFont, fontType, systemFontName, customFontPath, fontScale) {
        createCustomTypography(
            context = context,
            useCustomFont = useCustomFont,
            fontType = fontType,
            systemFontName = systemFontName,
            customFontPath = customFontPath,
            fontScale = fontScale
        )
    }

    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        if (useSystemTheme) {
            systemDarkTheme
        } else {
            themeMode == UserPreferencesManager.THEME_MODE_DARK
        }

    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var colorScheme =
        when {
            dynamicColor -> {
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            }
            darkTheme -> IosDarkColorScheme
            else -> IosLightColorScheme
        }

    if (useCustomColors) {
        customPrimaryColor?.let { primaryArgb ->
            val primary = Color(primaryArgb)
            val secondary = customSecondaryColor?.let { Color(it) } ?: colorScheme.secondary

            colorScheme = if (darkTheme) {
                generateDarkColorScheme(primary, secondary, onColorMode)
            } else {
                generateLightColorScheme(primary, secondary, onColorMode)
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = window.decorView.let { decorView ->
                androidx.core.view.WindowCompat.getInsetsController(window, decorView)
            }

            WindowCompat.setDecorFitsSystemWindows(window, false)

            if (statusBarHidden) {
                insetsController?.hide(WindowInsetsCompat.Type.statusBars())
                insetsController?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController?.show(WindowInsetsCompat.Type.statusBars())

                val statusBarColor = when {
                    statusBarTransparent -> Color.Transparent.toArgb()
                    useBackgroundImage && backgroundImageUri != null -> Color.Transparent.toArgb()
                    useCustomStatusBarColor && customStatusBarColorValue != null -> customStatusBarColorValue!!.toInt()
                    else -> if (darkTheme) IosDarkSystemBackground.toArgb() else IosLightSystemBackground.toArgb()
                }
                window.statusBarColor = statusBarColor
                insetsController?.isAppearanceLightStatusBars = !isColorLight(Color(statusBarColor))
            }

            if (useBackgroundImage && backgroundImageUri != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                insetsController?.isAppearanceLightNavigationBars = !darkTheme
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = true
                }
                val navColor = if (darkTheme) IosDarkSystemBackground else IosLightTabBarBackground
                window.navigationBarColor = navColor.toArgb()
                insetsController?.isAppearanceLightNavigationBars = !isColorLight(navColor)
            }
        }
    }

    val exoPlayer =
        remember(
            useBackgroundImage,
            backgroundImageUri,
            backgroundMediaType,
            videoBackgroundLoop,
            videoBackgroundMuted
        ) {
            if (useBackgroundImage &&
                backgroundImageUri != null &&
                backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_VIDEO
            ) {
                ExoPlayer.Builder(context)
                    .setLoadControl(
                        DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                5000,
                                10000,
                                500,
                                1000
                            )
                            .setTargetBufferBytes(5 * 1024 * 1024)
                            .setPrioritizeTimeOverSizeThresholds(true)
                            .build()
                    )
                    .build()
                    .apply {
                        repeatMode =
                            if (videoBackgroundLoop) Player.REPEAT_MODE_ALL
                            else Player.REPEAT_MODE_OFF
                        volume = if (videoBackgroundMuted) 0f else 1f
                        playWhenReady = true

                        try {
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(backgroundImageUri))
                                .build()
                            setMediaItem(mediaItem)
                            prepare()
                        } catch (e: Exception) {
                            AppLogger.e(
                                "OperitTheme",
                                "Error loading video background: ${e.message}",
                                e
                            )
                            coroutineScope.launch {
                                preferencesManager.saveThemeSettings(
                                    useBackgroundImage = false
                                )
                            }
                        }
                    }
            } else {
                null
            }
        }

    DisposableEffect(key1 = Unit) {
        onDispose {
            try {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                exoPlayer?.release()
            } catch (e: Exception) {
                AppLogger.e("OperitTheme", "ExoPlayer release error", e)
            }
        }
    }

    if (exoPlayer != null) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        exoPlayer.pause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        exoPlayer.play()
                    }
                    else -> {}
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val liquidGlassBackdrop = rememberLayerBackdrop()
        val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null

        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
            LocalWaterGlassState provides waterGlassState,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().layerBackdrop(liquidGlassBackdrop)
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(if (darkTheme) Color.Black else Color.White)
                            .then(
                                if (waterGlassState != null) {
                                    Modifier.liquefiable(waterGlassState)
                                } else {
                                    Modifier
                                },
                            )
                )

                if (useBackgroundImage && backgroundImageUri != null) {
                    val uri = Uri.parse(backgroundImageUri)
                    val coroutineScope = rememberCoroutineScope()

                    if (backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
                        val painter =
                            rememberAsyncImagePainter(
                                model = uri,
                                error =
                                    rememberAsyncImagePainter(
                                        if (darkTheme) Color.Black else Color.White
                                    ),
                            )

                        LaunchedEffect(painter) {
                            if (painter.state is AsyncImagePainter.State.Error) {
                                AppLogger.e(
                                    "OperitTheme",
                                    "Error loading background image from URI: $backgroundImageUri",
                                )

                                if (uri.scheme == "file") {
                                    val file = uri.path?.let { File(it) }
                                    if (file == null || !file.exists()) {
                                        AppLogger.e(
                                            "OperitTheme",
                                            "Internal file doesn't exist: ${file?.absolutePath}",
                                        )
                                    } else {
                                        AppLogger.e(
                                            "OperitTheme",
                                            "File exists but couldn't be loaded: ${file.absolutePath}, size: ${file.length()}",
                                        )
                                    }
                                }

                                coroutineScope.launch {
                                    preferencesManager.saveThemeSettings(useBackgroundImage = false)
                                }
                            }
                        }

                        Image(
                            painter = painter,
                            contentDescription = "Background Image",
                            modifier =
                                Modifier.fillMaxSize()
                                    .alpha(backgroundImageOpacity)
                                    .then(
                                        if (useBackgroundBlur) {
                                            Modifier.blur(radius = backgroundBlurRadius.dp)
                                        } else {
                                            Modifier
                                        },
                                    ).then(
                                        if (waterGlassState != null) {
                                            Modifier.liquefiable(waterGlassState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        exoPlayer?.let { player ->
                            val videoBackgroundColor =
                                if (darkTheme) {
                                    android.graphics.Color.BLACK
                                } else {
                                    android.graphics.Color.WHITE
                                }
                            AndroidView(
                                factory = { ctx ->
                                    (LayoutInflater.from(ctx).inflate(
                                        R.layout.view_background_texture_player,
                                        null,
                                        false,
                                    ) as StyledPlayerView).apply {
                                        this.player = player
                                        useController = false
                                        layoutParams =
                                            ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        setBackgroundColor(videoBackgroundColor)
                                        setShutterBackgroundColor(videoBackgroundColor)
                                        setKeepContentOnPlayerReset(true)
                                        foreground =
                                            android.graphics.drawable.ColorDrawable(
                                                android.graphics.Color.argb(
                                                    ((1f - backgroundImageOpacity) * 255).toInt(),
                                                    if (darkTheme) 0 else 255,
                                                    if (darkTheme) 0 else 255,
                                                    if (darkTheme) 0 else 255,
                                                )
                                            )
                                    }
                                },
                                update = { view ->
                                    if (view.player != player) {
                                        view.player = player
                                    }

                                    view.setBackgroundColor(videoBackgroundColor)
                                    view.setShutterBackgroundColor(videoBackgroundColor)
                                    view.setKeepContentOnPlayerReset(true)
                                    view.foreground =
                                        android.graphics.drawable.ColorDrawable(
                                            android.graphics.Color.argb(
                                                ((1f - backgroundImageOpacity) * 255).toInt(),
                                                if (darkTheme) 0 else 255,
                                                if (darkTheme) 0 else 255,
                                                if (darkTheme) 0 else 255,
                                            )
                                        )
                                },
                                modifier =
                                    Modifier.fillMaxSize().then(
                                        if (waterGlassState != null) {
                                            Modifier.liquefiable(waterGlassState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            if (useBackgroundImage && backgroundImageUri != null) {
                MaterialTheme(
                    colorScheme =
                        colorScheme.copy(
                            surface = colorScheme.surface.copy(alpha = 1f),
                            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = 1f),
                            background = colorScheme.background.copy(alpha = 1f),
                            surfaceContainer = colorScheme.surfaceContainer.copy(alpha = 1f),
                            surfaceContainerHigh =
                                colorScheme.surfaceContainerHigh.copy(alpha = 1f),
                            surfaceContainerHighest =
                                colorScheme.surfaceContainerHighest.copy(alpha = 1f),
                            surfaceContainerLow =
                                colorScheme.surfaceContainerLow.copy(alpha = 1f),
                            surfaceContainerLowest =
                                colorScheme.surfaceContainerLowest.copy(alpha = 1f),
                        ),
                    typography = customTypography,
                    content = content,
                )
            } else {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = customTypography,
                    content = content,
                )
            }
        }
    }
}

private fun generateLightColorScheme(
    primaryColor: Color,
    secondaryColor: Color,
    onColorMode: String
): ColorScheme {
    val onPrimary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(primaryColor)
    }
    val onSecondary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(secondaryColor)
    }

    val primaryContainer = lightenColor(primaryColor, 0.7f)
    val onPrimaryContainer = getContrastingTextColor(primaryContainer)
    val secondaryContainer = lightenColor(secondaryColor, 0.7f)
    val onSecondaryContainer = getContrastingTextColor(secondaryContainer)

    return IosLightColorScheme.copy(
        primary = primaryColor,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondaryColor,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        onSurface = Color.Black,
        onSurfaceVariant = Color.Black.copy(alpha = 0.7f),
        onBackground = Color.Black
    )
}

private fun generateDarkColorScheme(
    primaryColor: Color,
    secondaryColor: Color,
    onColorMode: String
): ColorScheme {
    val adjustedPrimaryColor = lightenColor(primaryColor, 0.2f)
    val adjustedSecondaryColor = lightenColor(secondaryColor, 0.2f)

    val onPrimary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(adjustedPrimaryColor)
    }
    val onSecondary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(adjustedSecondaryColor)
    }

    val primaryContainer = darkenColor(primaryColor, 0.3f)
    val onPrimaryContainer = getContrastingTextColor(primaryContainer, forceLight = true)
    val secondaryContainer = darkenColor(secondaryColor, 0.3f)
    val onSecondaryContainer = getContrastingTextColor(secondaryContainer, forceLight = true)

    return IosDarkColorScheme.copy(
        primary = adjustedPrimaryColor,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = adjustedSecondaryColor,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        onBackground = Color.White
    )
}

private fun getContrastingTextColor(
    backgroundColor: Color,
    forceDark: Boolean = false,
    forceLight: Boolean = false
): Color {
    if (forceDark) return Color.Black
    if (forceLight) return Color.White

    val luminance =
        0.299 * backgroundColor.red +
            0.587 * backgroundColor.green +
            0.114 * backgroundColor.blue

    return if (luminance > 0.5) Color.Black else Color.White
}

private fun lightenColor(color: Color, factor: Float): Color {
    val r = color.red + (1f - color.red) * factor
    val g = color.green + (1f - color.green) * factor
    val b = color.blue + (1f - color.blue) * factor
    return Color(r, g, b, color.alpha)
}

private fun darkenColor(color: Color, factor: Float): Color {
    val r = color.red * (1f - factor)
    val g = color.green * (1f - factor)
    val b = color.blue * (1f - factor)
    return Color(r, g, b, color.alpha)
}

private fun isColorLight(color: Color): Boolean {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5
}

private fun isColorDark(color: Color): Boolean {
    return !isColorLight(color)
}
