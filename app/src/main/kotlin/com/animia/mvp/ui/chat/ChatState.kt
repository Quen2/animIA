package com.animia.mvp.ui.chat

import com.animia.mvp.data.pubmed.PubMedArticle

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val expandedContent: String? = null
)

enum class Status { IDLE, CLASSIFYING, SEARCHING, THINKING, LISTENING }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: Status = Status.IDLE,
    val currentAnimal: String? = null,
    val currentScientificName: String? = null,
    val animalImageUrl: String? = null,
    val articles: List<PubMedArticle> = emptyList(),
    val expandedMessageIds: Set<Long> = emptySet(),
    val partialVoice: String = "",
    val error: String? = null
) {
    val hasConversation: Boolean get() = messages.isNotEmpty()
}
