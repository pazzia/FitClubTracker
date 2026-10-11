package com.fitclub.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.R
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.ui.theme.Fc

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

/**
 * A muscle group pictured with the user's own atlas illustration of its main muscle, so groups,
 * the atlas and exercise cards share one drawing style. Falls back to the older group drawing
 * only if the atlas picture cannot be read.
 */
@Composable
fun GroupImage(sex: Sex, group: MuscleGroup, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center) {
    val context = LocalContext.current.applicationContext
    val asset = groupAtlasPicture(group)
    val atlas by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, asset) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.assets.open(asset).use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    val resource = (if (sex == Sex.FEMALE) femaleGroups else maleGroups)[anatomyIndex(group)]
    val resources = LocalContext.current.resources
    Box(modifier.clipToBounds().background(Fc.AnatomyBackground)) {
        val picture = atlas
        if (picture != null) Image(picture, contentDescription = "${group.title}, ${sex.title.lowercase()} атлас",
            contentScale = ContentScale.Crop, alignment = Alignment.Center, modifier = Modifier.fillMaxSize())
        else {
            val bitmap = remember(resources, resource) { AnatomyCutouts.get(resources, resource).asImageBitmap() }
            Image(bitmap, contentDescription = "${group.title}, ${sex.title.lowercase()} атлас",
                contentScale = scale, alignment = alignment, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Atlas card of the muscle that names the group. */
fun groupAtlasPicture(group: MuscleGroup): String = "atlas/" + when (group) {
    MuscleGroup.CHEST -> "fit-2301"
    MuscleGroup.SHOULDERS -> "fit-2452"
    MuscleGroup.BICEPS -> "fit-2464"
    MuscleGroup.TRICEPS -> "fit-2471"
    MuscleGroup.FOREARMS -> "fit-2496"
    MuscleGroup.CORE -> "fit-2357"
    MuscleGroup.BACK -> "fit-2231"
    MuscleGroup.GLUTES -> "fit-2598"
    MuscleGroup.QUADS -> "fit-2613"
    MuscleGroup.HAMSTRINGS -> "fit-2638"
    MuscleGroup.CALVES -> "fit-2656"
} + "_thumb.webp"

@Composable
fun BodyPair(sex: Sex, modifier: Modifier = Modifier) {
    Image(painterResource(if (sex == Sex.FEMALE) R.drawable.body_female_pair else R.drawable.body_male_pair),
        contentDescription = "Карта мышечных групп: ${sex.title.lowercase()} атлас, спереди и сзади",
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().height(250.dp).background(Color(0xFF14191D)))
}
