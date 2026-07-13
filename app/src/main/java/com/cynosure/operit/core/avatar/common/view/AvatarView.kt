package com.cynosure.operit.core.avatar.common.view

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.cynosure.operit.core.avatar.common.control.AvatarController
import com.cynosure.operit.core.avatar.common.model.AvatarModel
import com.cynosure.operit.core.avatar.common.factory.AvatarRendererFactory

@Composable
fun AvatarView(
    modifier: Modifier = Modifier,
    model: AvatarModel,
    controller: AvatarController,
    rendererFactory: AvatarRendererFactory,
    onError: (String) -> Unit = {}
) {
    val renderer = rendererFactory.createRenderer(model)

    if (renderer != null) {
        renderer(modifier, controller)
    } else {
        val errorMessage = "Unsupported AvatarModel type: ${model.type}"
        SideEffect {
            onError(errorMessage)
        }
        Text(modifier = modifier, text = errorMessage)
    }
} 