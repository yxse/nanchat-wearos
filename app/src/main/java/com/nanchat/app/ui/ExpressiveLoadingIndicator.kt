package com.nanchat.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.toPath

/**
 * A small shape-morphing loading spinner in the spirit of Material 3 Expressive's LoadingIndicator.
 * That component only exists in phone Compose Material3 (there's no Wear Compose Material3
 * equivalent), and the release that has it requires compileSdk 37, which isn't available in this
 * project's SDK setup - so this is built directly on the same androidx.graphics.shapes primitives
 * (RoundedPolygon/Morph) that component uses under the hood, sized for a watch screen.
 */
@Composable
fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier, color: Color, indicatorSize: Dp = 32.dp) {
    val transition = rememberInfiniteTransition(label = "loading-indicator")
    val morphProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "morph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "rotation",
    )

    Box(
        modifier = modifier
            .size(indicatorSize)
            .graphicsLayer { rotationZ = rotation }
            .drawWithCache {
                val radius = size.minDimension / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val circle = RoundedPolygon.circle(numVertices = 24, radius = radius, centerX = cx, centerY = cy)
                val cookie = RoundedPolygon(
                    numVertices = 8,
                    radius = radius,
                    centerX = cx,
                    centerY = cy,
                    rounding = CornerRounding(radius * 0.4f),
                )
                val morph = Morph(circle, cookie)
                onDrawBehind {
                    drawPath(morph.toPath(progress = morphProgress).asComposePath(), color = color)
                }
            },
    )
}
