package com.animia.mvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.animia.mvp.ui.chat.ChatScreen
import com.animia.mvp.ui.theme.AnimIATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimIATheme {
                ChatScreen()
            }
        }
    }
}
