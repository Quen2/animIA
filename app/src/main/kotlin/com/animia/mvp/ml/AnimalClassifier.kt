package com.animia.mvp.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class AnimalGuess(
    val label: String,
    val scientificName: String?,
    val confidence: Float,
    val source: String
)

/**
 * Lance jusqu'à 4 modèles TFLite en parallèle (Birds, Insects, Plants, Generic ImageNet)
 * et retourne la meilleure prédiction (score le plus haut, normalisé par modèle).
 */
class AnimalClassifier(
    context: Context,
    maxResults: Int = 3,
    scoreThreshold: Float = 0.05f
) : AutoCloseable {

    private data class Loaded(val name: String, val classifier: ImageClassifier)

    private val loaded: List<Loaded> = buildList {
        ModelSpec.ALL.forEach { spec ->
            if (assetExists(context.assets, spec.assetName)) {
                runCatching {
                    val options = ImageClassifier.ImageClassifierOptions.builder()
                        .setBaseOptions(
                            BaseOptions.builder().setModelAssetPath(spec.assetName).build()
                        )
                        .setRunningMode(RunningMode.IMAGE)
                        .setMaxResults(maxResults)
                        .setScoreThreshold(scoreThreshold)
                        .build()
                    Loaded(spec.name, ImageClassifier.createFromOptions(context, options))
                }.onFailure {
                    Log.w(TAG, "Échec chargement modèle ${spec.assetName}: ${it.message}")
                }.getOrNull()?.let(::add)
            }
        }
    }

    val hasModels: Boolean get() = loaded.isNotEmpty()

    suspend fun classify(bitmap: Bitmap): AnimalGuess? = coroutineScope {
        if (loaded.isEmpty()) return@coroutineScope null
        loaded.map { entry ->
            async(Dispatchers.Default) { runOne(entry, bitmap) }
        }.awaitAll().filterNotNull().maxByOrNull { it.confidence }
    }

    private fun runOne(entry: Loaded, bitmap: Bitmap): AnimalGuess? {
        return runCatching {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = entry.classifier.classify(mpImage)
            val top = result.classificationResult().classifications()
                .firstOrNull()?.categories()?.firstOrNull() ?: return null
            val raw = top.categoryName()
            val (common, scientific) = parseLabel(raw)
            AnimalGuess(
                label = common,
                scientificName = scientific,
                confidence = top.score(),
                source = entry.name
            )
        }.onFailure {
            Log.w(TAG, "Erreur classify (${entry.name}): ${it.message}")
        }.getOrNull()
    }

    override fun close() {
        loaded.forEach { runCatching { it.classifier.close() } }
    }

    companion object {
        private const val TAG = "AnimalClassifier"

        private fun assetExists(assets: AssetManager, name: String): Boolean = runCatching {
            assets.openFd(name).close(); true
        }.getOrElse {
            // openFd échoue pour les fichiers compressés, on retombe sur open()
            runCatching { assets.open(name).close(); true }.getOrDefault(false)
        }

        /**
         * AIY V1 labels : "Genus species (Common Name)" → scientifique dehors, commun dedans.
         * ImageNet : "golden_retriever" → juste commun, pas de scientifique.
         * Heuristique : si une partie ressemble à du Latin binomial (deux mots commençant
         * par lettre, le 2e en minuscule) on la prend comme nom scientifique.
         */
        internal fun parseLabel(raw: String): Pair<String, String?> {
            val match = Regex("^(.+?)\\s*\\(([^)]+)\\)\\s*$").matchEntire(raw.trim())
            if (match != null) {
                val outside = match.groupValues[1].trim()
                val inside = match.groupValues[2].trim()
                val outsideIsLatin = looksLikeBinomial(outside)
                val insideIsLatin = looksLikeBinomial(inside)
                val (sciRaw, comRaw) = when {
                    outsideIsLatin && !insideIsLatin -> outside to inside
                    insideIsLatin && !outsideIsLatin -> inside to outside
                    outsideIsLatin -> outside to inside // les deux Latin : on garde l'ordre AIY
                    else -> null to outside // pas de Latin clair, on garde dehors comme commun
                }
                val common = (comRaw ?: outside).cleanName()
                return common to sciRaw
            }
            return raw.cleanName() to null
        }

        private val BINOMIAL = Regex("^[A-Z][a-z]+\\s+[a-z]+(\\s+[a-z]+)?$")
        private fun looksLikeBinomial(s: String): Boolean = BINOMIAL.matches(s.trim())

        private fun String.cleanName(): String = replace('_', ' ')
            .split(' ')
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
    }
}

data class ModelSpec(val name: String, val assetName: String) {
    companion object {
        val BIRDS = ModelSpec("birds", "animal_classifier_birds.tflite")
        val INSECTS = ModelSpec("insects", "animal_classifier_insects.tflite")
        val PLANTS = ModelSpec("plants", "animal_classifier_plants.tflite")
        val GENERIC = ModelSpec("generic", "animal_classifier_generic.tflite")
        val ALL = listOf(BIRDS, INSECTS, PLANTS, GENERIC)
    }
}
