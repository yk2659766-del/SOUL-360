package com.soul360.design.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Brand colours taken from the SOUL 360 DESIGN logo. */
object Brand {
    val Background = Color(0xFFF1E8D9)
    val Line = Color(0xFFAB9F8A)
    val Sun = Color(0xFFEB9E4E)
    val Wordmark = Color(0xFF6F614F)
    val WordmarkLight = Color(0xFFA39580)
}

private const val VB_W = 375f
private const val VB_H = 486f
private const val STROKE = 26f

private fun archPath() = Path().apply {
    moveTo(13f, 473f)
    lineTo(13f, 187.5f)
    arcTo(Rect(13f, 13f, 362f, 362f), 180f, 180f, false)
    lineTo(362f, 473f)
    close()
}

private fun curvePath() = Path().apply {
    moveTo(39f, 473f)
    lineTo(104f, 473f)
    cubicTo(159f, 473f, 181f, 420f, 214f, 367f)
    cubicTo(247f, 314f, 277f, 289f, 349f, 289f)
}

/**
 * The logo mark (arch, sun, hill). Each part can be partially drawn for animation:
 * progress values 0..1. `sunScale` lets loaders pulse the sun.
 */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    arch: Float = 1f,
    sun: Float = 1f,
    curve: Float = 1f,
    sunScale: Float = 1f,
    lineColor: Color = Brand.Line,
    sunColor: Color = Brand.Sun,
) {
    val archFull = remember { archPath() }
    val curveFull = remember { curvePath() }
    val archMeasure = remember { PathMeasure().apply { setPath(archFull, false) } }
    val curveMeasure = remember { PathMeasure().apply { setPath(curveFull, false) } }

    Canvas(modifier.aspectRatio(VB_W / VB_H)) {
        val s = minOf(size.width / VB_W, size.height / VB_H)
        val dx = (size.width - VB_W * s) / 2
        val dy = (size.height - VB_H * s) / 2
        translate(dx, dy) {
            scale(s, s, pivot = Offset.Zero) {
                val stroke = Stroke(width = STROKE, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
                if (sun > 0f) {
                    val r = 80f * sun * sunScale
                    drawCircle(sunColor, radius = r, center = Offset(187.5f, 187f + (1f - sun) * 60f), alpha = sun.coerceIn(0f, 1f))
                }
                if (arch >= 1f) drawPath(archFull, lineColor, style = stroke)
                else if (arch > 0f) {
                    val seg = Path()
                    archMeasure.getSegment(0f, archMeasure.length * arch, seg, true)
                    drawPath(seg, lineColor, style = stroke)
                }
                if (curve >= 1f) drawPath(curveFull, lineColor, style = stroke)
                else if (curve > 0f) {
                    val seg = Path()
                    curveMeasure.getSegment(0f, curveMeasure.length * curve, seg, true)
                    drawPath(seg, lineColor, style = stroke)
                }
            }
        }
    }
}

/** "SOUL 360 / DESIGN" wordmark in the logo's spaced, light style. */
@Composable
fun LogoWordmark(
    modifier: Modifier = Modifier,
    size: TextUnit = 26.sp,
    color: Color = Brand.Wordmark,
    subColor: Color = Brand.WordmarkLight,
    alpha: Float = 1f,
) {
    Column(modifier.graphicsLayer { this.alpha = alpha }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SOUL 360", color = color, fontSize = size, fontWeight = FontWeight.Light,
            letterSpacing = (size.value * 0.28f).sp)
        Text("DESIGN", color = subColor, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Normal,
            letterSpacing = (size.value * 0.55f).sp)
    }
}

/** Full logo: mark above wordmark. */
@Composable
fun FullLogo(markHeight: Dp, wordSize: TextUnit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LogoMark(Modifier.height(markHeight))
        Spacer(Modifier.height(markHeight * 0.12f))
        LogoWordmark(size = wordSize)
    }
}

/** Loader: the mark with a gently pulsing, glowing sun. */
@Composable
fun LogoLoader(modifier: Modifier = Modifier, lineColor: Color = Brand.Line) {
    val t = rememberInfiniteTransition(label = "logo-loader")
    val pulse by t.animateFloat(0.82f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sun")
    val draw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "curve")
    Box(modifier, contentAlignment = Alignment.Center) {
        LogoMark(Modifier.height(72.dp).width(56.dp), curve = draw, sunScale = pulse, lineColor = lineColor)
    }
}

private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

/**
 * Opening animation: the arch draws in, the sun rises, the hill curve sweeps across and the
 * wordmark gently settles into place. Then everything drifts up and dissolves. Tap to skip.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val done by rememberUpdatedState(onFinished)
    val arch = remember { Animatable(0f) }
    val sun = remember { Animatable(0f) }
    val curve = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(120)   // let the first frame settle so the animation starts smoothly
        launch { arch.animateTo(1f, tween(1100, easing = EaseInOutSine)) }
        delay(500)
        launch { sun.animateTo(1f, tween(900, easing = EaseOutQuint)) }
        delay(450)
        launch { curve.animateTo(1f, tween(850, easing = EaseInOutSine)) }
        delay(450)
        word.animateTo(1f, tween(900, easing = EaseOutQuint))
        delay(350)
        exit.animateTo(1f, tween(650, easing = EaseInOutSine))
        done()
    }

    Box(
        Modifier.fillMaxSize().background(Brand.Background).pointerInput(Unit) { detectTapGestures { done() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer {
                alpha = 1f - exit.value
                translationY = -40f * exit.value * density
                val sc = 1f - 0.04f * exit.value
                scaleX = sc; scaleY = sc
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoMark(Modifier.height(170.dp), arch = arch.value, sun = sun.value, curve = curve.value)
            Spacer(Modifier.height(26.dp))
            LogoWordmark(
                Modifier.graphicsLayer {
                    translationY = (1f - word.value) * 16f * density
                    val sc = 0.94f + 0.06f * word.value
                    scaleX = sc; scaleY = sc
                },
                size = 30.sp, alpha = word.value,
            )
        }
    }
}
