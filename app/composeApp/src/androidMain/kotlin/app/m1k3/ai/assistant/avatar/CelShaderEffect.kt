package app.m1k3.ai.assistant.avatar

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Cel-shading + pixelation post-process effect for 3D avatars.
 *
 * Applies an AGSL RuntimeShader that:
 * 1. Quantizes UV coordinates → pixel grid effect (retro feel)
 * 2. Posterizes color values → cel/toon shading (flat color bands)
 *
 * Requires API 33+ (Android 13). Graceful no-op on older devices.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.85 | context: AGSL shader for avatar style
 */

/**
 * AGSL shader source — pixelation + cel shading in one pass.
 *
 * `pixelSize` controls the pixel grid size (higher = more pixelated).
 * `colorLevels` controls color quantization (lower = more cartoony).
 */
private const val CEL_PIXEL_SHADER = """
    uniform shader content;
    uniform float pixelSize;
    uniform float colorLevels;

    half4 main(float2 coord) {
        // Pixelation: snap coordinates to grid
        float2 pixelCoord = floor(coord / pixelSize) * pixelSize + pixelSize * 0.5;
        half4 color = content.eval(pixelCoord);

        // Cel shading: quantize each color channel to discrete levels
        color.rgb = floor(color.rgb * colorLevels + 0.5) / colorLevels;

        return color;
    }
"""

/**
 * Wrap content with a cel-shading + pixelation post-process effect.
 *
 * @param pixelSize Size of the pixel grid in dp-ish units (3-8 = subtle, 10+ = chunky)
 * @param colorLevels Number of discrete color levels per channel (4 = very toony, 8 = subtle)
 * @param modifier Modifier for the container
 * @param content The composable to apply the effect to (typically the 3D Scene)
 */
@Composable
fun CelShaderEffect(
    pixelSize: Float = 4f,
    colorLevels: Float = 6f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // API 33+: apply AGSL shader
        val shader = RuntimeShader(CEL_PIXEL_SHADER).apply {
            setFloatUniform("pixelSize", pixelSize)
            setFloatUniform("colorLevels", colorLevels)
        }
        Box(
            modifier = modifier.graphicsLayer {
                renderEffect = android.graphics.RenderEffect
                    .createRuntimeShaderEffect(shader, "content")
                    .asComposeRenderEffect()
            }
        ) {
            content()
        }
    } else {
        // Pre-API 33: render without effect (graceful fallback)
        Box(modifier = modifier) {
            content()
        }
    }
}
