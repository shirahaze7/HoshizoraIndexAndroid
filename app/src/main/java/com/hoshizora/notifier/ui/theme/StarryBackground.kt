package com.hoshizora.notifier.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin
import kotlin.random.Random

data class Star(
    val relX: Float,
    val relY: Float,
    val size: Float,
    val twinkleFactor: Float,
    val initialPhase: Float
)

private val GlowColor = Color(0xFF7DD3FC)

@Composable
fun StarryBackground(
    modifier: Modifier = Modifier,
    isPaused: Boolean = false
) {
    val stars = remember {
        List(80) {
            val speed = Random.nextFloat() * 2000f + 1000f
            Star(
                relX = Random.nextFloat(),
                relY = Random.nextFloat(),
                size = Random.nextFloat() * 2.5f + 1f,
                twinkleFactor = 3000f / speed,
                initialPhase = Random.nextFloat() * 6.28f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "StarryBackground")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "StarryTime"
    )

    var lastActiveTime by remember { mutableFloatStateOf(0f) }
    if (!isPaused) {
        lastActiveTime = animatedTime
    }
    val time = lastActiveTime

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer() // GPUレイヤー化し、他コンポーネントの再描画領域と分離
            .drawBehind {
                val width = size.width
                val height = size.height

                stars.forEach { star ->
                    val x = star.relX * width
                    val y = star.relY * height

                    val twinkle = sin((time * star.twinkleFactor) + star.initialPhase)
                    val alpha = (0.2f + (twinkle + 1f) * 0.4f).coerceIn(0.2f, 1f)

                    // 星のコア（DrawScopeのalpha引数を使用し、毎フレームのColorインスタンス生成＝GCを防止）
                    drawCircle(
                        color = Color.White,
                        alpha = alpha,
                        radius = star.size,
                        center = Offset(x, y)
                    )

                    // 星のグロー
                    if (alpha > 0.6f) {
                        drawCircle(
                            color = GlowColor,
                            alpha = (alpha - 0.6f) * 0.3f,
                            radius = star.size * 2f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
    )
}
