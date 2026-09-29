package com.fitclub.tracker.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.ui.components.ScreenTitle
import com.fitclub.tracker.ui.theme.Fc

@Composable
fun ComingSoonScreen(title: String, note: String) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle(title)
        Text(note, style = MaterialTheme.typography.bodyLarge, color = Fc.Muted)
    }
}
