package app.m1k3.ai.assistant.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.m1k3.ai.assistant.avatar.AvatarEngine.drawRobotAvatar

/**
 * iOS implementation of AvatarViewContent3D
 *
 * Falls back to 2D Canvas rendering (3D not yet supported on iOS).
 * Future: Integrate SceneKit or Unity for 3D rendering.
 *
 * @param state Avatar state to render
 */
@Composable
actual fun AvatarViewContent3D(state: AvatarState) {
    // iOS: Fall back to 2D Canvas (3D not yet implemented)
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRobotAvatar(
            state = state,
            geometry = RobotGeometry(),
            animation = AvatarAnimation()
        )
    }
}
