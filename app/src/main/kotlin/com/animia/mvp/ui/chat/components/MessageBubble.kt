package com.animia.mvp.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.animia.mvp.ui.chat.ChatMessage
import com.animia.mvp.ui.chat.ChatRole
import com.animia.mvp.ui.theme.GreenPrimary

@Composable
fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    if (isUser) RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)
                    else RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
                )
                .background(if (isUser) Color.White else GreenPrimary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = message.content,
                color = if (isUser) Color.Black else Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
