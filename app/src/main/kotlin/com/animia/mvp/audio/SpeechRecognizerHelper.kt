package com.animia.mvp.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartial: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onListeningChange: (Boolean) -> Unit = {}
) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(localeTag: String = "fr-FR") {
        if (!isAvailable()) {
            onError("Reconnaissance vocale indisponible sur cet appareil.")
            return
        }
        stop()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        sr.setRecognitionListener(buildListener())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        recognizer = sr
        onListeningChange(true)
        sr.startListening(intent)
    }

    fun stop() {
        recognizer?.let {
            it.stopListening()
            it.cancel()
            it.destroy()
        }
        recognizer = null
        onListeningChange(false)
    }

    private fun buildListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            onListeningChange(false)
            recognizer = null
            if (!text.isNullOrBlank()) onResult(text)
        }

        override fun onError(error: Int) {
            onListeningChange(false)
            recognizer = null
            onError(errorMessage(error))
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Erreur audio"
        SpeechRecognizer.ERROR_CLIENT -> "Erreur interne"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission micro refusée"
        SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout réseau"
        SpeechRecognizer.ERROR_NO_MATCH -> "Je n'ai rien compris, réessaie."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconnaissance occupée"
        SpeechRecognizer.ERROR_SERVER -> "Erreur serveur"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Pas de parole détectée"
        else -> "Erreur inconnue ($code)"   
    }
}
