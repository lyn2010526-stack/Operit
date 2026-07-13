package com.cynosure.operit.core.avatar.impl.factory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cynosure.operit.core.avatar.common.control.AvatarController
import com.cynosure.operit.core.avatar.common.factory.AvatarRendererFactory
import com.cynosure.operit.core.avatar.common.model.AvatarModel
import com.cynosure.operit.core.avatar.common.model.AvatarType
import com.cynosure.operit.core.avatar.common.model.ISkeletalAvatarModel
import com.cynosure.operit.core.avatar.impl.dragonbones.view.DragonBonesRenderer
import com.cynosure.operit.core.avatar.impl.fbx.model.FbxAvatarModel
import com.cynosure.operit.core.avatar.impl.fbx.view.FbxRenderer
import com.cynosure.operit.core.avatar.impl.gltf.model.GltfAvatarModel
import com.cynosure.operit.core.avatar.impl.gltf.view.GltfRenderer
import com.cynosure.operit.core.avatar.impl.mmd.model.MmdAvatarModel
import com.cynosure.operit.core.avatar.impl.mmd.view.MmdRenderer
import com.cynosure.operit.core.avatar.impl.mp4.model.Mp4AvatarModel
import com.cynosure.operit.core.avatar.impl.mp4.view.Mp4Renderer
import com.cynosure.operit.core.avatar.impl.webp.model.WebPAvatarModel
import com.cynosure.operit.core.avatar.impl.webp.view.WebPRenderer

class AvatarRendererFactoryImpl : AvatarRendererFactory {

    @Composable
    override fun createRenderer(model: AvatarModel): @Composable ((modifier: Modifier, controller: AvatarController) -> Unit)? {
        return when (model.type) {
            AvatarType.DRAGONBONES -> {
                val skeletalModel = model as? ISkeletalAvatarModel
                if (skeletalModel != null) {
                    { modifier, controller ->
                        DragonBonesRenderer(
                            modifier = modifier,
                            model = skeletalModel,
                            controller = controller,
                            onError = { }
                        )
                    }
                } else {
                    null
                }
            }
            AvatarType.WEBP -> {
                val webpModel = model as? WebPAvatarModel
                if (webpModel != null) {
                    { modifier, controller ->
                        WebPRenderer(
                            modifier = modifier,
                            model = webpModel,
                            controller = controller,
                            onError = { }
                        )
                    }
                } else {
                    null
                }
            }
            AvatarType.MP4 -> {
                val mp4Model = model as? Mp4AvatarModel
                if (mp4Model != null) {
                    { modifier, controller ->
                        Mp4Renderer(
                            modifier = modifier,
                            model = mp4Model,
                            controller = controller,
                            onError = { }
                        )
                    }
                } else {
                    null
                }
            }
            AvatarType.MMD -> {
                val mmdModel = model as? MmdAvatarModel
                if (mmdModel != null) {
                    { modifier, controller ->
                        MmdRenderer(
                            modifier = modifier,
                            model = mmdModel,
                            controller = controller,
                            onError = { }
                        )
                    }
                } else {
                    null
                }
            }
            AvatarType.GLTF -> {
                val gltfModel = model as? GltfAvatarModel
                if (gltfModel != null) {
                    { modifier, controller ->
                        GltfRenderer(
                            modifier = modifier,
                            model = gltfModel,
                            controller = controller,
                            onError = { }
                        )
                    }
                } else {
                    null
                }
            }
            AvatarType.FBX -> {
                val fbxModel = model as? FbxAvatarModel
                if (fbxModel != null) {
                    { modifier, controller ->
                        FbxRenderer(
                            modifier = modifier,
                            model = fbxModel,
                            controller = controller,
                            onError = { }
                        )
                    }
                } else {
                    null
                }
            }
        }
    }
}
