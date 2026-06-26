package com.animia.mvp.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Résultat d'une reconnaissance de cri/son d'animal.
 *
 * @param displayName nom affiché à l'utilisateur (français)
 * @param searchName  nom commun anglais utilisé pour PubMed / Wikipédia
 * @param confidence  score YAMNet [0..1]
 * @param rawLabel    label brut AudioSet (debug)
 */
data class SoundGuess(
    val displayName: String,
    val searchName: String,
    val confidence: Float,
    val rawLabel: String
)

/**
 * Reconnaissance du **bruit** d'un animal enregistré au micro.
 *
 * Pipeline : AudioRecord (16 kHz mono PCM) → MediaPipe AudioClassifier (YAMNet, 521 classes
 * AudioSet) → on filtre les classes "animal" et on mappe le son (ex. "Meow") vers
 * l'animal réel (ex. chat / cat) pour réutiliser le même pipeline que la photo.
 *
 * YAMNet attend du 16 kHz mono. On enregistre ~4 s, on agrège la meilleure catégorie
 * animale sur toutes les fenêtres d'analyse.
 */
class AnimalSoundClassifier(context: Context) : AutoCloseable {

    private val classifier: AudioClassifier? = if (assetExists(context.assets, MODEL_ASSET)) {
        runCatching {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.AUDIO_CLIPS)
                .setMaxResults(MAX_RESULTS)
                .build()
            AudioClassifier.createFromOptions(context, options)
        }.onFailure { Log.w(TAG, "Échec chargement YAMNet: ${it.message}") }.getOrNull()
    } else {
        Log.w(TAG, "$MODEL_ASSET absent de assets/")
        null
    }

    val isAvailable: Boolean get() = classifier != null

    /**
     * Enregistre [durationMs] millisecondes au micro puis identifie l'animal.
     * Doit être appelé avec la permission RECORD_AUDIO déjà accordée.
     * Retourne null si rien d'exploitable (pas de modèle, pas d'animal détecté…).
     */
    suspend fun recordAndIdentify(durationMs: Int = RECORD_MS): SoundGuess? =
        withContext(Dispatchers.IO) {
            val clf = classifier ?: return@withContext null
            val samples = runCatching { record(durationMs) }
                .onFailure { Log.w(TAG, "Erreur enregistrement: ${it.message}") }
                .getOrNull() ?: return@withContext null
            if (samples.isEmpty()) return@withContext null
            classify(clf, samples)
        }

    private fun classify(clf: AudioClassifier, samples: FloatArray): SoundGuess? {
        val audioData = AudioData.create(
            AudioData.AudioDataFormat.builder()
                .setNumOfChannels(1)
                .setSampleRate(SAMPLE_RATE.toFloat())
                .build(),
            samples.size
        )
        audioData.load(samples)

        val result = runCatching { clf.classify(audioData) }
            .onFailure { Log.w(TAG, "Erreur classify audio: ${it.message}") }
            .getOrNull() ?: return null

        // Agrège : pour chaque fenêtre temporelle, on garde la meilleure catégorie animale
        var best: Pair<String, Float>? = null
        result.classificationResults().forEach { res ->
            res.classifications().forEach { clsf ->
                clsf.categories().forEach { cat ->
                    val name = cat.categoryName()
                    if (isAnimalLabel(name)) {
                        if (best == null || cat.score() > best!!.second) {
                            best = name to cat.score()
                        }
                    }
                }
            }
        }

        val (label, score) = best ?: return null
        if (score < MIN_CONFIDENCE) return null
        val (display, search) = mapAnimal(label)
        return SoundGuess(
            displayName = display,
            searchName = search,
            confidence = score,
            rawLabel = label
        )
    }

    @SuppressLint("MissingPermission")
    private fun record(durationMs: Int): FloatArray {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE) // au moins 1 s de marge

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )

        val totalSamples = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(totalSamples)
        val chunk = ShortArray(minBuf / 2)
        var offset = 0
        try {
            recorder.startRecording()
            while (offset < totalSamples) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) break
                val toCopy = minOf(read, totalSamples - offset)
                System.arraycopy(chunk, 0, out, offset, toCopy)
                offset += toCopy
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        // PCM 16-bit signé → float normalisé [-1, 1]
        val floats = FloatArray(offset)
        for (i in 0 until offset) {
            floats[i] = out[i] / 32768f
        }
        return floats
    }

    override fun close() {
        runCatching { classifier?.close() }
    }

    companion object {
        private const val TAG = "AnimalSoundClassifier"
        const val MODEL_ASSET = "yamnet.tflite"
        private const val SAMPLE_RATE = 16000
        private const val RECORD_MS = 4000
        private const val MAX_RESULTS = 5
        private const val MIN_CONFIDENCE = 0.10f

        private fun assetExists(assets: AssetManager, name: String): Boolean = runCatching {
            assets.open(name).close(); true
        }.getOrDefault(false)

        /**
         * Sons d'animaux dans l'ontologie AudioSet (labels YAMNet). On exclut les classes
         * génériques inutilisables ("Animal", "Wild animals"…) et tout ce qui n'est pas animal
         * (Speech, Music, Silence…).
         */
        private val ANIMAL_MAP: Map<String, Pair<String, String>> = mapOf(
            // chien
            "Dog" to ("Chien" to "dog"),
            "Bark" to ("Chien" to "dog"),
            "Bow-wow" to ("Chien" to "dog"),
            "Yip" to ("Chien" to "dog"),
            "Howl" to ("Loup / chien" to "wolf"),
            "Growling" to ("Chien" to "dog"),
            "Whimper (dog)" to ("Chien" to "dog"),
            "Canidae, dogs, wolves" to ("Canidé" to "Canidae"),
            // chat
            "Cat" to ("Chat" to "cat"),
            "Meow" to ("Chat" to "cat"),
            "Purr" to ("Chat" to "cat"),
            "Hiss" to ("Chat" to "cat"),
            "Caterwaul" to ("Chat" to "cat"),
            // gros félins
            "Roaring cats (lions, tigers)" to ("Lion / tigre" to "lion"),
            "Roar" to ("Lion" to "lion"),
            // bétail / ferme
            "Horse" to ("Cheval" to "horse"),
            "Clip-clop" to ("Cheval" to "horse"),
            "Neigh, whinny" to ("Cheval" to "horse"),
            "Cattle, bovinae" to ("Vache" to "cattle"),
            "Moo" to ("Vache" to "cattle"),
            "Cowbell" to ("Vache" to "cattle"),
            "Pig" to ("Cochon" to "pig"),
            "Oink" to ("Cochon" to "pig"),
            "Goat" to ("Chèvre" to "goat"),
            "Bleat" to ("Chèvre / mouton" to "goat"),
            "Sheep" to ("Mouton" to "sheep"),
            // volaille
            "Fowl" to ("Volaille" to "poultry"),
            "Chicken, rooster" to ("Poule / coq" to "chicken"),
            "Cluck" to ("Poule" to "chicken"),
            "Crowing, cock-a-doodle-doo" to ("Coq" to "rooster"),
            "Turkey" to ("Dinde" to "turkey"),
            "Gobble" to ("Dinde" to "turkey"),
            "Duck" to ("Canard" to "duck"),
            "Quack" to ("Canard" to "duck"),
            "Goose" to ("Oie" to "goose"),
            "Honk" to ("Oie" to "goose"),
            // oiseaux sauvages
            "Bird" to ("Oiseau" to "bird"),
            "Bird vocalization, bird call, bird song" to ("Oiseau" to "bird"),
            "Chirp, tweet" to ("Oiseau" to "bird"),
            "Squawk" to ("Oiseau" to "bird"),
            "Pigeon, dove" to ("Pigeon" to "pigeon"),
            "Coo" to ("Pigeon" to "pigeon"),
            "Crow" to ("Corbeau" to "crow"),
            "Caw" to ("Corbeau" to "crow"),
            "Owl" to ("Hibou" to "owl"),
            "Hoot" to ("Hibou" to "owl"),
            // rongeurs
            "Rodents, rats, mice" to ("Rongeur" to "rodent"),
            "Mouse" to ("Souris" to "mouse"),
            // insectes
            "Insect" to ("Insecte" to "insect"),
            "Cricket" to ("Grillon" to "cricket"),
            "Mosquito" to ("Moustique" to "mosquito"),
            "Fly, housefly" to ("Mouche" to "housefly"),
            "Buzz" to ("Abeille / insecte" to "bee"),
            "Bee, wasp, etc." to ("Abeille / guêpe" to "bee"),
            // amphibiens / reptiles
            "Frog" to ("Grenouille" to "frog"),
            "Croak" to ("Grenouille" to "frog"),
            "Snake" to ("Serpent" to "snake"),
            "Rattle" to ("Serpent à sonnette" to "rattlesnake"),
            // marin
            "Whale vocalization" to ("Baleine" to "whale")
        )

        private fun isAnimalLabel(label: String): Boolean = ANIMAL_MAP.containsKey(label)

        private fun mapAnimal(label: String): Pair<String, String> =
            ANIMAL_MAP[label] ?: (label to label)
    }
}
