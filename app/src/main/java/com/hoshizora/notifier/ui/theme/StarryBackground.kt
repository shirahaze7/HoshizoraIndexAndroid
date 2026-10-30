package com.hoshizora.notifier.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

data class Star(
    val position: Offset,
    val size: Float,
    val twinkleSpeed: Float,
    val initialPhase: Float
)

@Composable
fun StarryBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        List(100) {
            Star(
                position = Offset(
                    x = Random.nextFloat(),
                    y = Random.nextFloat()
                ),
                size = Random.nextFloat() * 2.5f + 1f,
                twinkleSpeed = Random.nextFloat() * 2000f + 1000f,
                initialPhase = Random.nextFloat() * 6.28f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "starTwinkle")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        stars.forEach { star ->
            val x = star.position.x * width
            val y = star.position.y * height
            
            val twinkle = kotlin.math.sin((time * (3000f / star.twinkleSpeed)) + star.initialPhase)
            val alpha = (0.2f + (twinkle + 1f) * 0.4f).coerceIn(0.2f, 1f)
            
            // 星のコア
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.size,
                center = Offset(x, y)
            )
            
            // 星のグロー
            if (alpha > 0.6f) {
                drawCircle(
                    color = Color(0xFF7DD3FC).copy(alpha = (alpha - 0.6f) * 0.3f),
                    radius = star.size * 2f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
