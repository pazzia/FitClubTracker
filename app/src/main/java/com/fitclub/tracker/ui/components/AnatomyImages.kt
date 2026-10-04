package com.fitclub.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.runtime.remember
import kotlin.math.min
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.R
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.model.Sex

private val maleGroups = listOf(R.drawable.group_male_0, R.drawable.group_male_1, R.drawable.group_male_2,
    R.drawable.group_male_3, R.drawable.group_male_4, R.drawable.group_male_5, R.drawable.group_male_6,
    R.drawable.group_male_7, R.drawable.group_male_8, R.drawable.group_male_9, R.drawable.group_male_10, R.drawable.group_male_11)
private val femaleGroups = listOf(R.drawable.group_female_0, R.drawable.group_female_1, R.drawable.group_female_2,
    R.drawable.group_female_3, R.drawable.group_female_4, R.drawable.group_female_5, R.drawable.group_female_6,
    R.drawable.group_female_7, R.drawable.group_female_8, R.drawable.group_female_9, R.drawable.group_female_10, R.drawable.group_female_11)

fun anatomyIndex(group: MuscleGroup): Int = when (group) {
    MuscleGroup.CHEST -> 0; MuscleGroup.SHOULDERS -> 1; MuscleGroup.BICEPS -> 2
    MuscleGroup.TRICEPS -> 3; MuscleGroup.FOREARMS -> 4; MuscleGroup.CORE -> 5
    MuscleGroup.BACK -> 7; MuscleGroup.GLUTES -> 8; MuscleGroup.QUADS -> 9
    MuscleGroup.HAMSTRINGS -> 10; MuscleGroup.CALVES -> 11
}

@Composable
fun GroupImage(sex: Sex, group: MuscleGroup, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit) {
    val resource = (if (sex == Sex.FEMALE) femaleGroups else maleGroups)[anatomyIndex(group)]
    Box(modifier.background(Color(0xFF14191D))) {
        Image(painterResource(resource), contentDescription = "${group.title}, ${sex.title.lowercase()} атлас",
            contentScale = scale, modifier = Modifier.fillMaxSize())
        if (sex == Sex.MALE && group == MuscleGroup.GLUTES) GlutePosingBrief(Modifier.fillMaxSize())
    }
}

@Composable
fun BodyPair(sex: Sex, modifier: Modifier = Modifier) {
    Image(painterResource(if (sex == Sex.FEMALE) R.drawable.body_female_pair else R.drawable.body_male_pair),
        contentDescription = "Карта мышечных групп: ${sex.title.lowercase()} атлас, спереди и сзади",
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().height(250.dp).background(Color(0xFF14191D)))
}



/** A separate garment layer preserves the source anatomy and leaves both glute regions exposed. */
@Composable
internal fun GlutePosingBrief(modifier: Modifier = Modifier) {
    val belt = remember { PathParser().parsePathString(
        "M80 34 Q199 62 320 34 L322 44 Q200 74 78 44 Z").toPath() }
    val strap = remember { PathParser().parsePathString(
        "M195 62 Q201 64 207 62 L205 183 Q207 205 202 218 L196 218 Q193 201 196 180 Z").toPath() }
    Canvas(modifier) {
        val factor = min(size.width, size.height) / 400f
        withTransform({
            translate((size.width - 400 * factor) / 2, (size.height - 400 * factor) / 2)
            scale(factor, factor, pivot = Offset.Zero)
        }) {
            drawPath(belt, Color(0xFF252D34))
            drawPath(strap, Color(0xFF20272D))
            drawPath(belt, Color(0xFF536069), style = Stroke(.9f))
            drawPath(strap, Color(0xFF536069), style = Stroke(.7f))
        }
    }
}
