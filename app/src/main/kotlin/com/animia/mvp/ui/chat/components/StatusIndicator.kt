package com.animia.mvp.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.animia.mvp.ui.chat.Status
import com.animia.mvp.ui.theme.GreenPrimary

@Composable
fun StatusIndicator(status: Status, modifier: Modifier = Modifier) {
    val label = when (status) {
        Status.CLASSIFYING -> "Identification en cours…"
        Status.SEARCHING -> "Recherche d'articles…"
        Status.THINKING -> "L'IA réfléchit…"
        Status.LISTENING -> "Écoute en cours…"
        Status.IDLE -> return
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = GreenPrimary,
            strokeWidth = 2.dp
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
