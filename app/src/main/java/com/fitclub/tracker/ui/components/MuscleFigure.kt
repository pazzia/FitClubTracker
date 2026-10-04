package com.fitclub.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.R
import com.fitclub.tracker.data.model.*
import com.fitclub.tracker.ui.theme.Fc
import kotlin.math.min

internal fun highlightedMuscles(target: Collection<Muscle>, aux: Collection<Muscle>): Pair<Set<Muscle>, Set<Muscle>> {
    val primary = target.toSet()
    return primary to (aux.toSet() - primary)
}

@Composable
fun MuscleFigure(sex: Sex, view: BodyView, target: Collection<Muscle>, aux: Collection<Muscle> = emptyList(),
    modifier: Modifier = Modifier, detailed: Boolean = true, description: String? = null) {
    val geometry = AthleteMusclePaths.forFigure(sex, view)
    val paths = remember(sex, view) { geometry.parts.mapValues { PathParser().parsePathString(it.value).toPath() } }
    val (primary, secondary) = highlightedMuscles(target, aux)
    val asset = when {
        sex == Sex.FEMALE && view == BodyView.FRONT -> R.drawable.body_female_front
        sex == Sex.FEMALE -> R.drawable.body_female_back
        view == BodyView.FRONT -> R.drawable.body_male_front
        else -> R.drawable.body_male_back
    }
    val visiblePrimary = primary.intersect(paths.keys)
    val visibleSecondary = secondary.intersect(paths.keys)
    val label = description ?: "${if (view == BodyView.FRONT) "Тело спереди" else "Тело сзади"}. Основные: ${visiblePrimary.joinToString { it.title }}. Дополнительные: ${visibleSecondary.joinToString { it.title }}"
    Box(modifier.aspectRatio(geometry.width / geometry.height).semantics { contentDescription = label }) {
        Image(painterResource(asset), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            // Match ContentScale.Fit exactly, including padding and the off-centre source silhouette.
            val factor = min(size.width / geometry.width, size.height / geometry.height)
            withTransform({
                translate((size.width - geometry.width * factor) / 2, (size.height - geometry.height * factor) / 2)
                scale(factor, factor, pivot = Offset.Zero)
            }) {
                paths.forEach { (muscle, path) ->
                    val color = when (muscle) { in primary -> Color(0xFFE6FF50); in secondary -> Color(0xFFFFA557); else -> null }
                    if (color != null) {
                        drawPath(path, color.copy(alpha = 0.66f))
                        withTransform({ scale(-1f, 1f, pivot = Offset(geometry.centre, 0f)) }) {
                            drawPath(path, color.copy(alpha = 0.66f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseMuscleMap(sex: Sex, primary: Collection<Muscle>, secondary: Collection<Muscle>) {
    val (target, aux) = highlightedMuscles(primary, secondary)
    FcCard {
        Text("Карта мышечных групп", color = Fc.Text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                MuscleFigure(sex, BodyView.FRONT, target, aux, Modifier.fillMaxWidth())
                Text("Спереди", color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                MuscleFigure(sex, BodyView.BACK, target, aux, Modifier.fillMaxWidth())
                Text("Сзади", color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("● Основные: ${target.joinToString { it.title }}", color = Color(0xFFE6FF50), style = MaterialTheme.typography.bodySmall)
        if (aux.isNotEmpty()) Text("● Дополнительные: ${aux.joinToString { it.title }}", color = Color(0xFFFFA557), style = MaterialTheme.typography.bodySmall)
    }
}
