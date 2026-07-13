package com.cynosure.operit.ui.features.token

import androidx.compose.runtime.Composable
import com.cynosure.operit.ui.features.settings.screens.TokenUsageStatisticsScreen

@Composable
fun TokenConfigNativeScreen(
    onNavigateBack: () -> Unit
) {
    TokenUsageStatisticsScreen(onBackPressed = onNavigateBack)
}
