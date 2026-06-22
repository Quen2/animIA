package com.animia.mvp.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.animia.mvp.ui.theme.GreenAccent
import com.animia.mvp.ui.theme.GreenMist

/**
 * Avatar circulaire pour l'animal en cours :
 * - Affiche la photo Wikipédia si dispo
 * - Sinon retombe sur la mascotte AnimIA
 */
@Composable
fun AnimalAvatar(imageUrl: String?, size: Dp = 72.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GreenMist)
            .border(2.dp, GreenAccent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Photo de l'animal",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Mascot(size = size * 0.9f, withBackground = false)
        }
    }
}
