package tss.t.tsiptv.utils

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * iOS 26 Liquid Glass Effect Configuration
 */
data class LiquidGlassConfig(
    val blurRadius: Dp = 24.dp,
    val baseColor: Color = Color.White.copy(alpha = 0.15f),
    val accentColor: Color = Color.White.copy(alpha = 0.1f),
    val shimmerIntensity: Float = 0.3f,
    val flowSpeed: Float = 1f,
    val liquidDistortion: Float = 0.8f,
    val depthLayers: Int = 3,
    val borderWidth: Dp = 0.5.dp,
    val borderColor: Color = Color.White.copy(alpha = 0.4f),
    val shimmer: Boolean = false,
)

/**
 * Creates the signature iOS 26 Liquid Glass effect with dynamic fluid animations
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    config: LiquidGlassConfig = LiquidGlassConfig(),
    shimmer: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquidGlass")

    val shimmerOffset = 0f
    val depthPulse = 0.8f
    var liquidFlow by remember {
        mutableStateOf(0f)
    }
    if (shimmer) {
        val liquidFlowAnimate by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2 * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween((4000 / config.flowSpeed).toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "liquidFlow"
        )
        LaunchedEffect(shimmer, liquidFlowAnimate) {
            liquidFlow = liquidFlowAnimate
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier.wrapContentSize(),
            content = content
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .clickable(onClick = onClick)
                .drawBehind {
                    drawLiquidGlassBackground(
                        config = config,
                        shimmerOffset = shimmerOffset,
                        liquidFlow = liquidFlow,
                        depthPulse = depthPulse,
                        shape = shape
                    )
                }
                .border(
                    width = config.borderWidth,
                    color = config.borderColor,
                    shape = shape
                ),
            content = {}
        )
    }
}

/**
 * Draws the liquid glass background with multiple layers and effects
 */
private fun DrawScope.drawLiquidGlassBackground(
    config: LiquidGlassConfig,
    shimmerOffset: Float,
    liquidFlow: Float,
    depthPulse: Float,
    shape: Shape,
) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // Draw multiple depth layers
    repeat(config.depthLayers) { layer ->
        val layerAlpha = (1f - layer * 0.3f) * depthPulse
        val layerOffset = layer * 20f

        // Create liquid gradient for this layer
        val liquidGradient = createLiquidGradient(
            config = config,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            liquidFlow = liquidFlow + layer * 0.5f,
            layerAlpha = layerAlpha,
            layerOffset = layerOffset
        )

        // Draw the liquid layer
        drawRect(
            brush = liquidGradient,
            alpha = layerAlpha
        )
    }

    // Draw shimmer effect
    drawShimmerEffect(
        config = config,
        shimmerOffset = shimmerOffset,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight
    )

    // Draw liquid distortion overlay
    drawLiquidDistortion(
        config = config,
        liquidFlow = liquidFlow,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight
    )
}

/**
 * Creates a dynamic liquid gradient that flows and changes
 */
private fun createLiquidGradient(
    config: LiquidGlassConfig,
    canvasWidth: Float,
    canvasHeight: Float,
    liquidFlow: Float,
    layerAlpha: Float,
    layerOffset: Float,
): Brush {
    val centerX = canvasWidth / 2f
    val centerY = canvasHeight / 2f

    // Create flowing gradient points
    val gradientRadius = max(canvasWidth, canvasHeight) * 0.8f
    val flowX = centerX + cos(liquidFlow) * layerOffset * config.liquidDistortion
    val flowY = centerY + sin(liquidFlow * 1.3f) * layerOffset * config.liquidDistortion

    return Brush.radialGradient(
        colors = listOf(
            config.accentColor.copy(alpha = config.accentColor.alpha * layerAlpha),
            config.baseColor.copy(alpha = config.baseColor.alpha * layerAlpha),
            Color.Transparent
        ),
        center = Offset(flowX, flowY),
        radius = gradientRadius
    )
}

/**
 * Draws the shimmer effect that moves across the surface
 */
private fun DrawScope.drawShimmerEffect(
    config: LiquidGlassConfig,
    shimmerOffset: Float,
    canvasWidth: Float,
    canvasHeight: Float,
) {
    val shimmerWidth = canvasWidth * 0.3f
    val shimmerX = (canvasWidth + shimmerWidth) * shimmerOffset - shimmerWidth

    val shimmerGradient = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = config.shimmerIntensity),
            Color.Transparent
        ),
        start = Offset(shimmerX, 0f),
        end = Offset(shimmerX + shimmerWidth, canvasHeight)
    )

    drawRect(brush = shimmerGradient)
}

/**
 * Draws liquid distortion patterns
 */
private fun DrawScope.drawLiquidDistortion(
    config: LiquidGlassConfig,
    liquidFlow: Float,
    canvasWidth: Float,
    canvasHeight: Float,
) {
    val path = Path()
    val waveAmplitude = canvasHeight * 0.1f * config.liquidDistortion
    val waveFrequency = 3f

    // Create flowing wave pattern
    path.moveTo(0f, canvasHeight / 2f)

    for (x in 0..canvasWidth.toInt() step 10) {
        val normalizedX = x / canvasWidth
        val waveY = canvasHeight / 2f +
                sin(normalizedX * waveFrequency * 2 * PI + liquidFlow) * waveAmplitude +
                sin(normalizedX * waveFrequency * 4 * PI + liquidFlow * 1.5f) * waveAmplitude * 0.5f

        path.lineTo(x.toFloat(), waveY.toFloat())
    }

    path.lineTo(canvasWidth, canvasHeight)
    path.lineTo(0f, canvasHeight)
    path.close()

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                config.accentColor.copy(alpha = 0.05f)
            )
        )
    )
}

/**
 * Preset configurations for different liquid glass styles
 */
object LiquidGlassPresets {
    val Classic = LiquidGlassConfig()

    val Vibrant = LiquidGlassConfig(
        baseColor = Color.White.copy(alpha = 0.2f),
        accentColor = Color.White.copy(alpha = 0.15f),
        shimmerIntensity = 0.4f,
        liquidDistortion = 1.2f
    )

    val Subtle = LiquidGlassConfig(
        baseColor = Color.White.copy(alpha = 0.08f),
        accentColor = Color.White.copy(alpha = 0.05f),
        shimmerIntensity = 0.2f,
        flowSpeed = 0.5f,
        liquidDistortion = 0.5f
    )

    val Dynamic = LiquidGlassConfig(
        baseColor = Color.White.copy(alpha = 0.18f),
        accentColor = Color.White.copy(alpha = 0.12f),
        shimmerIntensity = 0.5f,
        flowSpeed = 1.5f,
        liquidDistortion = 1.5f,
        depthLayers = 4
    )

    val Dark = LiquidGlassConfig(
        baseColor = Color.Black.copy(alpha = 0.3f),
        accentColor = Color.White.copy(alpha = 0.2f),
        shimmerIntensity = 0.3f,
        borderColor = Color.White.copy(alpha = 0.2f)
    )
}

/**
 * Convenience composable for quick liquid glass cards
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    preset: LiquidGlassConfig = LiquidGlassPresets.Classic,
    shimmer: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        config = preset,
        content = content,
        onClick = onClick,
        shimmer = shimmer
    )
}

/**
 * Liquid glass button with enhanced interaction effects
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preset: LiquidGlassConfig = LiquidGlassPresets.Classic,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        config = preset,
        content = content
    )
}