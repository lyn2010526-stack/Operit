package com.ai.assistance.operit.ui.common.ios

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.IosBlue
import com.ai.assistance.operit.ui.theme.IosGreen
import com.ai.assistance.operit.ui.theme.IosRed
import com.ai.assistance.operit.ui.theme.IosOrange
import com.ai.assistance.operit.ui.theme.IosLightSystemBackground
import com.ai.assistance.operit.ui.theme.IosLightSecondarySystemBackground
import com.ai.assistance.operit.ui.theme.IosLightPrimaryLabel
import com.ai.assistance.operit.ui.theme.IosLightSecondaryLabel
import com.ai.assistance.operit.ui.theme.IosDarkSystemBackground
import com.ai.assistance.operit.ui.theme.IosDarkSecondarySystemBackground
import com.ai.assistance.operit.ui.theme.IosDarkPrimaryLabel
import com.ai.assistance.operit.ui.theme.IosDarkSecondaryLabel
import com.ai.assistance.operit.ui.theme.IosLightSeparator
import com.ai.assistance.operit.ui.theme.IosDarkSeparator
import com.ai.assistance.operit.ui.theme.IosCardShape
import com.ai.assistance.operit.ui.theme.IosButtonShape
import com.ai.assistance.operit.ui.theme.IosPillShape

object IosColors {
    @Composable
    fun systemBackground(): Color {
        return if (isSystemInDarkTheme()) IosDarkSystemBackground else IosLightSystemBackground
    }

    @Composable
    fun secondarySystemBackground(): Color {
        return if (isSystemInDarkTheme()) IosDarkSecondarySystemBackground else IosLightSecondarySystemBackground
    }

    @Composable
    fun primaryLabel(): Color {
        return if (isSystemInDarkTheme()) IosDarkPrimaryLabel else IosLightPrimaryLabel
    }

    @Composable
    fun secondaryLabel(): Color {
        return if (isSystemInDarkTheme()) IosDarkSecondaryLabel else IosLightSecondaryLabel
    }

    @Composable
    fun separator(): Color {
        return if (isSystemInDarkTheme()) IosDarkSeparator else IosLightSeparator
    }

    @Composable
    fun tintColor(): Color = IosBlue

    @Composable
    fun destructiveColor(): Color = IosRed
}

@Composable
fun IosNavigationBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = IosColors.systemBackground(),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                TextButton(
                    onClick = onBackClick,
                    modifier = Modifier.padding(start = 4.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = IosColors.tintColor(),
                        modifier = Modifier
                            .size(28.dp)
                            .padding(start = 0.dp)
                    )
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.bodyLarge,
                        color = IosColors.tintColor()
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = IosColors.primaryLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

@Composable
fun IosNavigationBarWithLargeTitle(
    title: String,
    onBackClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(IosColors.systemBackground())
    ) {
        IosNavigationBar(
            title = "",
            onBackClick = onBackClick,
            trailingContent = trailingContent
        )
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = IosColors.primaryLabel(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
    }
}

@Composable
fun IosTabBar(
    tabs: List<IosTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badgeCounts: Map<Int, Int> = emptyMap()
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = IosColors.systemBackground(),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp)
                .padding(top = 0.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) IosColors.tintColor() else IosColors.secondaryLabel(),
                    animationSpec = tween(200)
                )
                val badgeCount = badgeCounts[index]

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(index) }
                        .padding(top = 6.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.title,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                            if (badgeCount != null && badgeCount > 0) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-4).dp),
                                    shape = CircleShape,
                                    color = IosRed,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = iconColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

data class IosTabItem(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@Composable
fun IosListSection(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = IosColors.secondaryLabel(),
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = IosCardShape,
            color = IosColors.systemBackground(),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 2.dp),
                content = content
            )
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = IosColors.secondaryLabel(),
                modifier = Modifier.padding(start = 16.dp, top = 6.dp)
            )
        }
    }
}

@Composable
fun IosListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    trailingText: String? = null,
    showChevron: Boolean = false,
    showCheckmark: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val contentColor = if (!enabled) {
        IosColors.secondaryLabel().copy(alpha = 0.5f)
    } else if (isDestructive) {
        IosColors.destructiveColor()
    } else {
        IosColors.primaryLabel()
    }

    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (onClick != null && enabled) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(29.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background((iconTint ?: IosColors.tintColor()).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: IosColors.tintColor(),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = IosColors.secondaryLabel(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyLarge,
                color = IosColors.secondaryLabel(),
                maxLines = 1
            )
            if (showChevron) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        if (showCheckmark) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = IosColors.tintColor(),
                modifier = Modifier.size(20.dp)
            )
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IosColors.separator().copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (onClick != null) {
        HorizontalDivider(
            modifier = Modifier.padding(start = if (icon != null) 57.dp else 16.dp),
            thickness = 0.5.dp,
            color = IosColors.separator()
        )
    }
}

@Composable
fun IosToggleRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val contentColor = if (!enabled) {
        IosColors.secondaryLabel().copy(alpha = 0.5f)
    } else {
        IosColors.primaryLabel()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(29.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background((iconTint ?: IosColors.tintColor()).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: IosColors.tintColor(),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = IosColors.secondaryLabel(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IosGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = IosColors.separator().copy(alpha = 0.4f),
                disabledCheckedTrackColor = IosGreen.copy(alpha = 0.3f)
            )
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = if (icon != null) 57.dp else 16.dp),
        thickness = 0.5.dp,
        color = IosColors.separator()
    )
}
