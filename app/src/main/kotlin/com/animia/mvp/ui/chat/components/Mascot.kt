package com.animia.mvp.ui.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.animia.mvp.R
import com.animia.mvp.ui.theme.GreenMist

@Composable
fun Mascot(size: Dp = 160.dp, withBackground: Boolean = true) {
    if (withBackground) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(GreenMist),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.mascot),
                contentDescription = "Mascotte AnimIA",
                modifier = Modifier.size(size * 0.82f)
            )
        }
    } else {
        Image(
            painter = painterResource(id = R.drawable.mascot),
            contentDescription = "Mascotte AnimIA",
            modifier = Modifier.size(size)
        )
    }
}
