package com.animia.mvp.data.groq

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.4,
    @SerialName("max_tokens") val maxTokens: Int = 800,
    val stream: Boolean = false
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqChatResponse(
    val id: String? = null,
    val choices: List<GroqChoice> = emptyList()
)

@Serializable
data class GroqChoice(
    val index: Int = 0,
    val message: GroqMessage
)
