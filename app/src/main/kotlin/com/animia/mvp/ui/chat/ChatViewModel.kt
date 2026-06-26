package com.animia.mvp.ui.chat

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.animia.mvp.data.AnimIARepository
import com.animia.mvp.data.SystemPrompts
import com.animia.mvp.data.groq.GroqClient
import com.animia.mvp.data.groq.GroqMessage
import com.animia.mvp.data.pubmed.PubMedArticle
import com.animia.mvp.data.wikipedia.WikipediaClient
import com.animia.mvp.audio.AnimalSoundClassifier
import com.animia.mvp.ml.AnimalClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AnimIARepository()
    private val idGenerator = AtomicLong(0)

    private var classifier: AnimalClassifier? = null
    private var soundClassifier: AnimalSoundClassifier? = null
    private var lastAbstractsBlob: String = ""

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    // ---------- API publique pour l'UI ----------

    fun sendUserText(text: String) {
        if (text.isBlank()) return
        if (!GroqClient.isApiKeyConfigured()) {
            _state.update { it.copy(error = "Clé Groq manquante. Voir INSTALLATION.md.") }
            return
        }
        val trimmed = text.trim()
        appendMessage(ChatRole.USER, trimmed)
        viewModelScope.launch {
            // Si pas encore d'animal en contexte, tente de chercher des articles depuis la question
            if (_state.value.articles.isEmpty()) {
                searchArticlesFromText(trimmed)
            }
            runChat()
        }
    }

    private suspend fun searchArticlesFromText(text: String) {
        _state.update { it.copy(status = Status.SEARCHING) }

        // Étape 1 : faire extraire le nom propre par Groq (FR → EN+Latin)
        val (extractedCommon, extractedSci) = runCatching {
            repository.extractAnimalKeywords(text)
        }.getOrNull() ?: (null to null)

        // Étape 2 : fallback regex Latin sur le texte si Groq n'a rien trouvé
        val regexBinomial = extractBinomial(text)
        val finalScientific = extractedSci ?: regexBinomial
        val finalCommon = extractedCommon ?: regexBinomial ?: text

        _state.update {
            it.copy(
                currentAnimal = it.currentAnimal ?: extractedCommon ?: finalScientific ?: text.take(60),
                currentScientificName = it.currentScientificName ?: finalScientific
            )
        }
        loadArticlesAndImage(scientific = finalScientific, common = finalCommon)
    }

    /**
     * Charge articles + image avec garantie de résultat :
     * 1. PubMed (jusqu'à 30 articles via cascade)
     * 2. Image Wikipédia (toujours fetchée)
     * 3. Si PubMed vide → crée 1 article synthétique depuis l'extrait Wikipédia
     */
    private suspend fun loadArticlesAndImage(scientific: String?, common: String?) {
        val (pubmedArticles, abstractsBlob) = runCatching {
            repository.searchScientificArticles(scientificName = scientific, commonName = common)
        }.getOrDefault(emptyList<PubMedArticle>() to "")

        val wiki = runCatching {
            WikipediaClient.fetchArticle(scientific, common)
        }.getOrNull()

        val articles = if (pubmedArticles.isNotEmpty()) {
            pubmedArticles
        } else if (wiki != null) {
            listOf(synthesizeArticle(wiki, common ?: scientific ?: "Animal"))
        } else {
            emptyList()
        }

        lastAbstractsBlob = when {
            abstractsBlob.isNotBlank() -> abstractsBlob
            wiki != null -> wiki.extract
            else -> ""
        }

        // Image en cascade : article Wikipédia → recherche image seule en fallback
        val imageUrl = wiki?.imageUrl
            ?: runCatching { WikipediaClient.findImage(scientific, common) }.getOrNull()

        _state.update {
            it.copy(
                articles = articles,
                animalImageUrl = it.animalImageUrl ?: imageUrl
            )
        }
    }

    private fun synthesizeArticle(wiki: com.animia.mvp.data.wikipedia.WikiArticleData, animal: String): PubMedArticle {
        return PubMedArticle(
            pmid = "wiki-${wiki.lang}-${wiki.title.hashCode()}",
            title = "Wikipédia : ${wiki.title}",
            authors = "Contributeurs Wikipédia",
            journal = "Wikipédia (${wiki.lang.uppercase()})",
            year = java.time.Year.now().toString(),
            url = wiki.url,
            abstractText = wiki.extract
        )
    }

    private fun extractBinomial(text: String): String? {
        // Détecte un nom Latin binomial dans le texte (ex. "Felis catus", "Panthera tigris")
        return Regex("""\b[A-Z][a-z]{2,}\s+[a-z]{3,}\b""").find(text)?.value
    }

    fun onImageCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(status = Status.CLASSIFYING, error = null) }
                val classifier = ensureClassifier()
                if (classifier == null || !classifier.hasModels) {
                    _state.update {
                        it.copy(
                            status = Status.IDLE,
                            error = "Aucun modèle d'identification trouvé dans assets/. Voir INSTALLATION.md."
                        )
                    }
                    return@launch
                }
                val top = classifier.classify(bitmap)
                if (top == null) {
                    _state.update {
                        it.copy(status = Status.IDLE, error = "Aucun animal détecté sur la photo.")
                    }
                    return@launch
                }
                val displayName = top.label
                _state.update {
                    it.copy(
                        currentAnimal = displayName,
                        currentScientificName = top.scientificName,
                        status = Status.SEARCHING
                    )
                }

                loadArticlesAndImage(scientific = top.scientificName, common = displayName)

                val intro = buildString {
                    append("J'ai pris en photo : $displayName")
                    top.scientificName?.let { append(" (*$it*)") }
                    append(". Peux-tu m'en parler ?")
                }
                appendMessage(ChatRole.USER, intro)
                runChat()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(status = Status.IDLE, error = t.message ?: "Erreur inattendue.")
                }
            }
        }
    }

    fun onVoiceTranscript(text: String) {
        sendUserText(text)
    }

    /**
     * Enregistre ~4 s de son au micro et tente d'identifier l'animal à son cri.
     * La permission RECORD_AUDIO doit déjà être accordée par l'UI.
     */
    fun recordAnimalSound() {
        if (_state.value.status == Status.RECORDING) return
        viewModelScope.launch {
            try {
                val sound = ensureSoundClassifier()
                if (sound == null || !sound.isAvailable) {
                    _state.update {
                        it.copy(
                            status = Status.IDLE,
                            error = "Modèle de sons (yamnet.tflite) absent de assets/."
                        )
                    }
                    return@launch
                }
                _state.update { it.copy(status = Status.RECORDING, error = null) }
                val guess = sound.recordAndIdentify()
                if (guess == null) {
                    _state.update {
                        it.copy(
                            status = Status.IDLE,
                            error = "Je n'ai pas reconnu de cri d'animal. Réessaie plus près du son."
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        currentAnimal = guess.displayName,
                        currentScientificName = null,
                        status = Status.SEARCHING
                    )
                }
                loadArticlesAndImage(scientific = null, common = guess.searchName)

                val intro = "J'ai entendu un cri qui ressemble à : ${guess.displayName}. Peux-tu m'en parler ?"
                appendMessage(ChatRole.USER, intro)
                runChat()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(status = Status.IDLE, error = t.message ?: "Erreur lors de l'écoute.")
                }
            }
        }
    }

    fun setListening(listening: Boolean) {
        _state.update {
            it.copy(
                status = if (listening) Status.LISTENING else if (it.status == Status.LISTENING) Status.IDLE else it.status
            )
        }
    }

    fun updatePartialVoice(text: String) {
        _state.update { it.copy(partialVoice = text) }
    }

    fun reportError(message: String) {
        _state.update { it.copy(error = message, status = Status.IDLE) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Toggle entre version courte et version détaillée de la dernière réponse.
     * Au 1er clic : fetch la version détaillée via Groq et l'affiche.
     * Aux clics suivants : bascule simplement entre courte et longue (pas de re-fetch).
     */
    fun toggleLastAnswerDetails() {
        val lastAssistant = _state.value.messages.lastOrNull { it.role == ChatRole.ASSISTANT } ?: return

        if (lastAssistant.expandedContent != null) {
            // Toggle pur, pas de re-fetch
            _state.update { state ->
                val newSet = if (state.expandedMessageIds.contains(lastAssistant.id)) {
                    state.expandedMessageIds - lastAssistant.id
                } else {
                    state.expandedMessageIds + lastAssistant.id
                }
                state.copy(expandedMessageIds = newSet)
            }
            return
        }

        // 1er clic : fetch la version détaillée
        viewModelScope.launch {
            try {
                _state.update { it.copy(status = Status.THINKING, error = null) }
                val systemContent = SystemPrompts.withArticles(
                    animal = _state.value.currentAnimal ?: "(non identifié)",
                    abstracts = lastAbstractsBlob
                ) + "\n\n" + SystemPrompts.DETAILED_INSTRUCTION
                val history = mutableListOf<GroqMessage>().apply {
                    add(GroqMessage("system", systemContent))
                    _state.value.messages.forEach { msg ->
                        val role = if (msg.role == ChatRole.USER) "user" else "assistant"
                        add(GroqMessage(role, msg.content))
                    }
                    add(GroqMessage("user", "Développe ta dernière réponse avec plus de détails."))
                }
                val detailed = repository.askChat(history)
                if (detailed.isNotBlank()) {
                    _state.update { state ->
                        val updated = state.messages.map {
                            if (it.id == lastAssistant.id) it.copy(expandedContent = detailed) else it
                        }
                        state.copy(
                            messages = updated,
                            expandedMessageIds = state.expandedMessageIds + lastAssistant.id,
                            status = Status.IDLE
                        )
                    }
                } else {
                    _state.update { it.copy(status = Status.IDLE) }
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(status = Status.IDLE, error = t.message ?: "Erreur de l'assistant.")
                }
            }
        }
    }

    fun resetConversation() {
        lastAbstractsBlob = ""
        _state.value = ChatUiState()
    }

    // ---------- Interne ----------

    private fun runChat() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(status = Status.THINKING, error = null) }
                val systemContent = SystemPrompts.withArticles(
                    animal = _state.value.currentAnimal ?: "(non identifié)",
                    abstracts = lastAbstractsBlob
                )
                val history = mutableListOf<GroqMessage>().apply {
                    add(GroqMessage("system", systemContent))
                    _state.value.messages.forEach { msg ->
                        val role = if (msg.role == ChatRole.USER) "user" else "assistant"
                        add(GroqMessage(role, msg.content))
                    }
                }
                val answer = repository.askChat(history)
                if (answer.isNotBlank()) {
                    appendMessage(ChatRole.ASSISTANT, answer)
                }
                _state.update { it.copy(status = Status.IDLE) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        status = Status.IDLE,
                        error = t.message ?: "Erreur de l'assistant."
                    )
                }
            }
        }
    }

    private fun appendMessage(role: ChatRole, content: String) {
        val msg = ChatMessage(idGenerator.incrementAndGet(), role, content)
        _state.update { it.copy(messages = it.messages + msg) }
    }

    private fun ensureClassifier(): AnimalClassifier? {
        if (classifier != null) return classifier
        return runCatching { AnimalClassifier(getApplication()) }
            .onFailure { classifier = null }
            .getOrNull()
            .also { classifier = it }
    }

    private fun ensureSoundClassifier(): AnimalSoundClassifier? {
        if (soundClassifier != null) return soundClassifier
        return runCatching { AnimalSoundClassifier(getApplication()) }
            .onFailure { soundClassifier = null }
            .getOrNull()
            .also { soundClassifier = it }
    }

    override fun onCleared() {
        classifier?.close()
        classifier = null
        soundClassifier?.close()
        soundClassifier = null
        super.onCleared()
    }
}
