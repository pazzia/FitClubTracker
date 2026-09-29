package com.fitclub.tracker.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fitclub.tracker.data.model.BodyView
import com.fitclub.tracker.data.model.Muscle
import com.fitclub.tracker.data.model.Sex
import kotlin.math.max

private class ParsedPart(val muscle: Muscle, val angle: Float, val path: Path) {
    val bounds: Rect = path.getBounds()
}

private class ParsedFigure(data: FigureData) {
    private fun p(s: String) = PathParser().parsePathString(s).toPath()
    val silhouette = p(data.silhouette)
    val trunks = p(data.trunks)
    val veinsFront = p(data.veinsFront)
    val veinsBack = p(data.veinsBack)
    val front = data.front.map { ParsedPart(it.muscle, it.fiberAngle, p(it.path)) }
    val back = data.back.map { ParsedPart(it.muscle, it.fiberAngle, p(it.path)) }
}

private val NEUTRAL = listOf(Color(0xFFF1F2F5), Color(0xFFB9BCC3), Color(0xFF7B7E87))
private val TARGET = listOf(Color(0xFFFF7A55), Color(0xFFF0302F), Color(0xFFA3122A))
private val AUX = listOf(Color(0xFFFFD28E), Color(0xFFFF9A3C), Color(0xFFC2641C))
private val GLOW = Color(0xFF4D63FF)

/**
 * Серый «глиняный» манекен в стиле 3D-рендера.
 * Целевые мышцы — красные с синим свечением, вспомогательные — оранжевые.
 * [detailed] добавляет мышечные волокна и вены (для крупных размеров).
 */
@Composable
fun MuscleFigure(
    sex: Sex,
    view: BodyView,
    target: Collection<Muscle>,
    aux: Collection<Muscle> = emptyList(),
    modifier: Modifier = Modifier,
    detailed: Boolean = true,
    description: String? = null
) {
    val figure = remember(sex) { ParsedFigure(if (sex == Sex.FEMALE) BodyShapes.female else BodyShapes.male) }
    val targetSet = target.toSet()
    val auxSet = aux.toSet() - targetSet
    Canvas(
        modifier
            .aspectRatio(200f / 440f)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
    ) {
        val k = size.width / 200f
        scale(k, k, pivot = Offset.Zero) {
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), center = Offset(100f, 428f), radius = 52f),
                topLeft = Offset(48f, 420f), size = Size(104f, 16f)
            )
            drawPath(
                figure.silhouette,
                Brush.horizontalGradient(
                    0f to Color(0xFF595C65), 0.28f to Color(0xFFA7AAB2), 0.5f to Color(0xFFCDCFD5),
                    0.72f to Color(0xFFA0A3AB), 1f to Color(0xFF52555D), startX = 34f, endX = 166f
                )
            )
            drawPath(figure.silhouette, Color(0xFF474A52), style = Stroke(0.8f))

            val parts = if (view == BodyView.FRONT) figure.front else figure.back
            val veins = if (view == BodyView.FRONT) figure.veinsFront else figure.veinsBack
            drawSide(parts, veins, targetSet, auxSet, detailed)
            withTransform({ scale(-1f, 1f, pivot = Offset(100f, 0f)) }) {
                drawSide(parts, veins, targetSet, auxSet, detailed)
            }
            if (view == BodyView.FRONT) {
                drawPath(figure.trunks, Brush.verticalGradient(listOf(Color(0xFF2E3038), Color(0xFF0E0F13)), startY = 188f, endY = 226f))
            }
            drawOval(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = Offset(91f, 22f), radius = 16f),
                topLeft = Offset(85f, 13f), size = Size(20f, 30f)
            )
        }
    }
}

private fun DrawScope.drawSide(
    parts: List<ParsedPart>, veins: Path, target: Set<Muscle>, aux: Set<Muscle>, detailed: Boolean
) {
    parts.forEach { part ->
        val isTarget = part.muscle in target
        val isAux = !isTarget && part.muscle in aux
        if (isTarget || isAux) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = GLOW.copy(alpha = if (isTarget) 1f else 0.5f).toArgb()
                    maskFilter = BlurMaskFilter(if (isTarget) 3.2f else 2.4f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawPath(part.path.asAndroidPath(), paint)
            }
        }
        val b = part.bounds
        val colors = when { isTarget -> TARGET; isAux -> AUX; else -> NEUTRAL }
        drawPath(
            part.path,
            Brush.radialGradient(
                0f to colors[0], 0.55f to colors[1], 1f to colors[2],
                center = Offset(b.left + b.width * 0.38f, b.top + b.height * 0.3f),
                radius = max(b.width, b.height) * 0.8f
            )
        )
        drawPath(part.path, Color(0xFF6F727B), style = Stroke(0.8f))
        if (detailed) drawFibers(part)
    }
    if (detailed) {
        drawPath(veins, Color(0xFF7F8AA3).copy(alpha = 0.45f), style = Stroke(width = 0.7f, cap = StrokeCap.Round))
    }
}

/** Мышечные волокна: тонкая штриховка вдоль хода мышцы, обрезанная по её контуру. */
private fun DrawScope.drawFibers(part: ParsedPart) {
    val b = part.bounds
    val half = max(b.width, b.height)
    clipPath(part.path) {
        rotate(part.angle, pivot = b.center) {
            var x = -half
            while (x < half) {
                drawLine(Color.Black.copy(alpha = 0.17f), Offset(b.center.x + x, b.center.y - half), Offset(b.center.x + x, b.center.y + half), 0.55f)
                drawLine(Color.White.copy(alpha = 0.11f), Offset(b.center.x + x + 1.2f, b.center.y - half), Offset(b.center.x + x + 1.2f, b.center.y + half), 0.45f)
                x += 2.4f
            }
        }
    }
}
