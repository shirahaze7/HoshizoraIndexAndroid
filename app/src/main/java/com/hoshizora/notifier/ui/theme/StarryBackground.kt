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
        List(80) {
            Star(
                position = Offset(
                    x = Random.nextFloat(),
                    y = Random.nextFloat()
                ),
                size = Random.nextFloat() * 2f + 0.5f,
                twinkleSpeed = Random.nextFloat() * 2000f + 1000f,
                initialPhase = Random.nextFloat() * 2f * Math.PI.toFloat()
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "starTwinkle")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f, // 2π
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
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
            
            // 瞬き効果: sin波で透明度を変化
            val twinkle = kotlin.math.sin(time + star.initialPhase).toFloat()
            val alpha = 0.3f + twinkle * 0.7f
            
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.size,
                center = Offset(x, y)
            )
        }
    }
}
