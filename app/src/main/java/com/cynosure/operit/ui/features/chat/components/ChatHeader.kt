package com.cynosure.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cynosure.operit.R
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.cynosure.operit.ui.theme.IosBlue
import com.cynosure.operit.ui.theme.IosLightNavBarBackground
import com.cynosure.operit.ui.theme.IosDarkNavBarBackground
import com.cynosure.operit.ui.theme.IosLightSecondaryLabel
import com.cynosure.operit.ui.theme.IosLightPrimaryLabel
import com.cynosure.operit.ui.theme.IosDarkPrimaryLabel
import com.cynosure.operit.ui.theme.IosDarkSecondaryLabel
import com.cynosure.operit.ui.theme.IosLightSeparator
import com.cynosure.operit.ui.theme.IosDarkSeparator
import com.cynosure.operit.ui.theme.IosPillShape

private const val CHAT_HEADER_CHARACTER_NAME_MAX_LENGTH = 12

private fun String.toChatHeaderName(maxLength: Int = CHAT_HEADER_CHARACTER_NAME_MAX_LENGTH): String {
    return if (length <= maxLength) this else take(maxLength) + "\u2026"
}

@Composable
fun ChatHeader(
    showChatHistorySelector: Boolean,
    onToggleChatHistorySelector: () -> Unit,
    modifier: Modifier = Modifier,
    onLaunchFloatingWindow: () -> Unit = {},
    isFloatingMode: Boolean = false,
    historyIconColor: Int? = null,
    pipIconColor: Int? = null,
    runningTaskCount: Int = 0,
    activeCharacterName: String,
    activeCharacterAvatarUri: String?,
    onCharacterClick: () -> Unit
) {
    val displayCharacterName = activeCharacterName.toChatHeaderName()
    val isDark = isSystemInDarkTheme()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(vertical = 2.dp)
    ) {
        if (runningTaskCount >= 2) {
            Surface(
                onClick = onToggleChatHistorySelector,
                modifier = Modifier.height(32.dp),
                shape = IosPillShape,
                color = IosBlue.copy(alpha = 0.12f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.height(32.dp).padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription =
                            if (showChatHistorySelector) stringResource(R.string.hide_history) else stringResource(R.string.show_history),
                        tint = historyIconColor?.let { Color(it) } ?: IosBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = runningTaskCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = IosBlue,
                        maxLines = 1
                    )
                }
            }
        } else {
            Box(modifier = Modifier.size(34.dp)) {
                IconButton(
                    onClick = onToggleChatHistorySelector,
                    modifier = Modifier.matchParentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription =
                            if (showChatHistorySelector) stringResource(R.string.hide_history) else stringResource(R.string.show_history),
                        tint = historyIconColor?.let { Color(it) }
                            ?: if (showChatHistorySelector) IosBlue
                            else if (isDark) IosDarkSecondaryLabel else IosLightSecondaryLabel,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.size(34.dp)) {
            IconButton(
                onClick = onLaunchFloatingWindow,
                modifier = Modifier.matchParentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPicture,
                    contentDescription =
                        if (isFloatingMode) stringResource(R.string.close_floating_window) else stringResource(R.string.open_floating_window),
                    tint = pipIconColor?.let { Color(it) }
                        ?: if (isFloatingMode) IosBlue
                        else if (isDark) IosDarkSecondaryLabel else IosLightSecondaryLabel,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .widthIn(max = 176.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onCharacterClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(IosBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (activeCharacterAvatarUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(activeCharacterAvatarUri)),
                        contentDescription = "Character Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = "Character Avatar",
                        tint = IosBlue,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }
            Text(
                text = displayCharacterName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) IosDarkPrimaryLabel else IosLightPrimaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 116.dp)
            )
        }
    }
}
