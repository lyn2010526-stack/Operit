package com.cynosure.operit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(13.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

val IosPillShape = RoundedCornerShape(percent = 50)

val IosChatBubbleShape = RoundedCornerShape(18.dp)

val IosCardShape = RoundedCornerShape(13.dp)

val IosTextFieldShape = RoundedCornerShape(10.dp)

val IosButtonShape = RoundedCornerShape(10.dp)

val IosTabBarShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
