package com.cynosure.operit.ui.features.websession.browser

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cynosure.operit.data.model.SerializableColorScheme
import com.cynosure.operit.data.model.SerializableTypography
import com.cynosure.operit.data.model.toComposeColorScheme
import com.cynosure.operit.data.model.toComposeTypography
import com.cynosure.operit.services.FloatingChatService
import com.cynosure.operit.ui.floating.FloatingWindowTheme
import com.google.gson.Gson

private const val FLOATING_PREFS_NAME = "floating_chat_prefs"
private const val PREF_KEY_COLOR_SCHEME = "floating_color_scheme_json"
private const val PREF_KEY_TYPOGRAPHY = "floating_typography_json"

private data class WebSessionFloatingThemeSnapshot(
    val colorScheme: ColorScheme? = null,
    val typography: Typography? = null
)

@Composable
internal fun WebSessionFloatingTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val gson = remember { Gson() }
    var snapshot by remember { mutableStateOf(loadFloatingThemeSnapshot(context, gson)) }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == PREF_KEY_COLOR_SCHEME || key == PREF_KEY_TYPOGRAPHY) {
                    snapshot = loadFloatingThemeSnapshot(context, gson)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val liveService = FloatingChatService.getInstance()
    FloatingWindowTheme(
        colorScheme = liveService?.getColorScheme() ?: snapshot.colorScheme,
        typography = liveService?.getTypography() ?: snapshot.typography
    ) {
        content()
    }
}

private fun loadFloatingThemeSnapshot(
    context: Context,
    gson: Gson
): WebSessionFloatingThemeSnapshot {
    val prefs = context.getSharedPreferences(FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
    val colorScheme =
        prefs
            .getString(PREF_KEY_COLOR_SCHEME, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching {
                    gson.fromJson(raw, SerializableColorScheme::class.java).toComposeColorScheme()
                }.getOrNull()
            }
    val typography =
        prefs
            .getString(PREF_KEY_TYPOGRAPHY, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching {
                    gson.fromJson(raw, SerializableTypography::class.java).toComposeTypography()
                }.getOrNull()
            }
    return WebSessionFloatingThemeSnapshot(
        colorScheme = colorScheme,
        typography = typography
    )
}
