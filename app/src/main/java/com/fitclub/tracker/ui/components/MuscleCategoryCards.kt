package com.fitclub.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.NumberStyle
import androidx.compose.ui.unit.sp

val AnatomyGroupOrder = listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS,
    MuscleGroup.FOREARMS, MuscleGroup.CORE, MuscleGroup.BACK, MuscleGroup.GLUTES, MuscleGroup.QUADS,
    MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES, MuscleGroup.ADDUCTORS)

@Composable
fun MuscleCategoryRow(groups: List<MuscleGroup>, sex: Sex, counts: Map<MuscleGroup, Int>, onGroup: (MuscleGroup) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { group ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Fc.Surface)
                .border(1.dp, Fc.Line2, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onGroup(group) }) {
                GroupImage(sex, group, Modifier.fillMaxWidth().height(112.dp).padding(start = 10.dp, end = 10.dp, top = 10.dp),
                    scale = ContentScale.Fit, alignment = Alignment.BottomCenter)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title, color = Fc.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${counts[group] ?: 0}", color = Fc.Muted, style = NumberStyle.copy(fontSize = 15.sp))
                }
            }
        }
        if (groups.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
fun QuadricepsLegend() {
    Text("4 головки: прямая, латеральная широкая, медиальная широкая и промежуточная широкая. Промежуточная широкая лежит глубже прямой мышцы и не видна на поверхностном изображении.",
        color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
}
