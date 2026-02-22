package app.m1k3.ai.assistant.avatar

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Defines multipreview configurations for Avatar 3D views.
 * This annotation applies both a light and dark theme preview.
 */
@Preview(name = "Light Theme", showBackground = true)
@Preview(name = "Dark Theme", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
annotation class AvatarPreviews

@AvatarPreviews
@Composable
fun Avatar3DView_DefaultPreview() {
    Avatar3DView(
        state = AvatarState(emotion = AvatarEmotion.NEUTRAL),
        modifier = Modifier.size(280.dp)
    )
}

@AvatarPreviews
@Composable
fun Avatar3DView_LoadingPreview() {
    // This preview will show the initial loading state of the Avatar3DView.
    // The loading is triggered by the modelConfig change.
    Avatar3DView(
        state = AvatarState(emotion = AvatarEmotion.NEUTRAL),
        modifier = Modifier.size(280.dp),
        showLoadingIndicator = true
    )
}

@AvatarPreviews
@Composable
fun Avatar3DView_GeckoInteractivePreview() {
    Avatar3DView(
        state = AvatarState(emotion = AvatarEmotion.HAPPY),
        modifier = Modifier.size(280.dp),
        modelConfig = ModelRegistry.getById("gecko") ?: ModelRegistry.getDefault(),
        enableInteraction = true
    )
}

@AvatarPreviews
@Composable
fun Avatar3DViewCompact_Preview() {
    Avatar3DViewCompact(
        state = AvatarState(emotion = AvatarEmotion.NEUTRAL)
    )
}

@AvatarPreviews
@Composable
fun AllAvatars_ColumnPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.background(Color.Gray.copy(alpha = 0.2f))
    ) {
        Avatar3DView(
            state = AvatarState(emotion = AvatarEmotion.HAPPY),
            modifier = Modifier.size(150.dp),
            modelConfig = ModelRegistry.getById("colobus") ?: ModelRegistry.getDefault(),
            enableInteraction = true
        )
        Avatar3DView(
            state = AvatarState(emotion = AvatarEmotion.SAD),
            modifier = Modifier.size(150.dp),
            modelConfig = ModelRegistry.getById("sparrow") ?: ModelRegistry.getDefault(),
            enableInteraction = false
        )
        Avatar3DViewCompact(
            state = AvatarState(emotion = AvatarEmotion.EXCITED)
        )
    }
}
