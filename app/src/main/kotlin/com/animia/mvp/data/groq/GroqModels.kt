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

// ----- Requête multimodale (vision) -----
// Le contenu d'un message vision est une liste de "parts" (texte + image), d'où des
// types dédiés : on ne touche pas à GroqMessage (content: String) utilisé pour le chat.

@Serializable
data class GroqVisionRequest(
    val model: String,
    val messages: List<GroqVisionMessage>,
    val temperature: Double = 0.0,
    @SerialName("max_tokens") val maxTokens: Int = 120
)

@Serializable
data class GroqVisionMessage(
    val role: String,
    val content: List<GroqContentPart>
)

@Serializable
data class GroqContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: GroqImageUrl? = null
)

@Serializable
data class GroqImageUrl(val url: String)

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
