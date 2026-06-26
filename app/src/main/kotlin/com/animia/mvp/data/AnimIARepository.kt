package com.animia.mvp.data

import com.animia.mvp.data.groq.GroqChatRequest
import com.animia.mvp.data.groq.GroqClient
import com.animia.mvp.data.groq.GroqContentPart
import com.animia.mvp.data.groq.GroqImageUrl
import com.animia.mvp.data.groq.GroqMessage
import com.animia.mvp.data.groq.GroqVisionMessage
import com.animia.mvp.data.groq.GroqVisionRequest
import com.animia.mvp.data.pubmed.PubMedArticle
import com.animia.mvp.data.pubmed.PubMedClient

/** Résultat d'identification d'un animal par le modèle de vision. */
data class VisionAnimal(
    val common: String?,
    val scientific: String?,
    val confidence: Int
)

class AnimIARepository {

    private val groq = GroqClient.api
    private val pubmed = PubMedClient.api

    /**
     * Recherche d'articles avec stratégie en cascade :
     * 1. Nom scientifique binomial via tag MeSH [Organism] (très ciblé)
     * 2. Nom scientifique en plein texte (Title/Abstract)
     * 3. Genre seul (premier mot du binomial)
     * 4. Nom commun en plein texte
     * On s'arrête dès qu'on a `minResults` articles.
     */
    suspend fun searchScientificArticles(
        scientificName: String? = null,
        commonName: String? = null,
        maxResults: Int = 30,
        minResults: Int = 20
    ): Pair<List<PubMedArticle>, String> {
        val strategies = buildList {
            scientificName?.takeIf { it.isNotBlank() }?.let { sci ->
                add("\"$sci\"[Organism]")
                add("\"$sci\"[Title/Abstract]")
                sci.split(' ').firstOrNull()?.takeIf { it.length > 3 }?.let { genus ->
                    add("\"$genus\"[Organism]")
                }
            }
            commonName?.takeIf { it.isNotBlank() }?.let { com ->
                add("$com[Title/Abstract]")
                add(com)
            }
        }

        // On agrège (union) les IDs de toutes les stratégies pour ramener un max d'articles
        val collected = linkedSetOf<String>()
        for (query in strategies) {
            val result = runCatching {
                pubmed.search(term = query, retMax = maxResults).esearchresult.idlist
            }.getOrDefault(emptyList())
            collected.addAll(result)
            if (collected.size >= maxResults) break
        }
        val foundIds = collected.take(maxResults).toList()

        if (foundIds.isEmpty()) return emptyList<PubMedArticle>() to ""

        val idsJoined = foundIds.joinToString(",")
        val summary = pubmed.summary(ids = idsJoined)
        val articles = PubMedClient.parseSummary(summary)

        val abstractsBlob = runCatching {
            pubmed.fetchAbstracts(ids = idsJoined)
        }.getOrDefault("")

        val abstracts = PubMedClient.splitAbstracts(abstractsBlob)
        val merged = articles.mapIndexed { idx, art ->
            art.copy(abstractText = abstracts.getOrNull(idx))
        }
        return merged to abstractsBlob
    }

    /**
     * Identifie l'animal directement à partir de la photo via le modèle de vision Groq.
     * Bien plus fiable que les modèles TFLite locaux (gère basse résolution, toutes espèces).
     * @param base64Jpeg image JPEG encodée en base64 (sans le préfixe data:)
     * @return l'animal identifié, ou null si aucun animal / erreur réseau.
     */
    suspend fun identifyAnimalFromImage(base64Jpeg: String): VisionAnimal? {
        val prompt = """
            Tu es un zoologiste expert en identification d'animaux à partir de photos.
            Identifie l'animal principal sur l'image, même si la photo est de mauvaise qualité.
            Réponds STRICTEMENT dans ce format, sans aucun autre texte :
            COMMON: <nom commun en français, ou N/A si aucun animal visible>
            SCIENTIFIC: <nom scientifique latin "Genus species", ou N/A si incertain>
            CONFIDENCE: <entier de 0 à 100 indiquant ta certitude>
        """.trimIndent()

        val request = GroqVisionRequest(
            model = GroqClient.VISION_MODEL,
            messages = listOf(
                GroqVisionMessage(
                    role = "user",
                    content = listOf(
                        GroqContentPart(type = "text", text = prompt),
                        GroqContentPart(
                            type = "image_url",
                            imageUrl = GroqImageUrl("data:image/jpeg;base64,$base64Jpeg")
                        )
                    )
                )
            )
        )

        val text = groq.chatCompletionsVision(
            authorization = GroqClient.authHeader(),
            request = request
        ).choices.firstOrNull()?.message?.content?.trim().orEmpty()

        fun field(key: String): String? = Regex("$key:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }

        val common = field("COMMON")
        val scientific = field("SCIENTIFIC")
        val confidence = Regex("CONFIDENCE:\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        if (common == null && scientific == null) return null
        return VisionAnimal(common = common, scientific = scientific, confidence = confidence)
    }

    /** Appelle Groq avec l'historique de conversation. */
    suspend fun askChat(messages: List<GroqMessage>): String {
        val response = groq.chatCompletions(
            authorization = GroqClient.authHeader(),
            request = GroqChatRequest(
                model = GroqClient.DEFAULT_MODEL,
                messages = messages
            )
        )
        return response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
    }

    /**
     * Extrait le nom de l'animal d'une question en français.
     * Retourne (nom commun anglais, nom scientifique Latin) — l'un ou l'autre peut être null.
     * Sert à fiabiliser les requêtes PubMed/Wikipédia qui attendent un nom propre.
     */
    suspend fun extractAnimalKeywords(text: String): Pair<String?, String?> {
        val sysPrompt = """
            Tu es un extracteur d'entités. À partir d'une question en français,
            identifie l'animal principal mentionné.
            Réponds STRICTEMENT au format suivant, rien d'autre :
            COMMON: <english common name, 1-3 mots, minuscule>
            SCIENTIFIC: <Latin binomial Genus species, ou N/A si inconnu>

            Exemples :
            Question : "Parle-moi du tigre du Bengale"
            COMMON: bengal tiger
            SCIENTIFIC: Panthera tigris tigris

            Question : "Qu'est-ce qu'un axolotl ?"
            COMMON: axolotl
            SCIENTIFIC: Ambystoma mexicanum

            Question : "Donne-moi des infos sur les pandas"
            COMMON: giant panda
            SCIENTIFIC: Ailuropoda melanoleuca

            Si aucun animal n'est identifiable :
            COMMON: N/A
            SCIENTIFIC: N/A
        """.trimIndent()

        val response = runCatching {
            askChat(listOf(
                GroqMessage("system", sysPrompt),
                GroqMessage("user", text)
            ))
        }.getOrNull().orEmpty()

        val common = Regex("COMMON:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(response)?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
        val scientific = Regex("SCIENTIFIC:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(response)?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }

        return common to scientific
    }
}

object SystemPrompts {
    val BASE = """
        Tu es AnimIA, un assistant scientifique francophone spécialisé dans les animaux.
        Tu réponds toujours en français, de façon claire, vulgarisée mais rigoureuse.

        RÈGLES IMPORTANTES :
        - Réponds TOUJOURS directement à la question avec des informations concrètes.
        - Format par défaut : RÉPONSE COURTE de 1 à 2 phrases maximum (40 mots max).
        - Donne l'essentiel : l'utilisateur peut demander plus de détails ensuite.
        - Ne demande JAMAIS à l'utilisateur de fournir des articles ou des sources.
          Les articles scientifiques sont déjà affichés sous ta réponse, il les voit.
        - Si peu ou pas d'extraits sont fournis, utilise tes connaissances générales
          fiables sur l'animal pour répondre quand même, sans mentionner ce manque.
        - N'invente PAS de citations bibliographiques précises.
        - Ne mentionne jamais "PubMed", "Wikipédia", "système" ou ton fonctionnement
          interne. Parle uniquement de l'animal.
    """.trimIndent()

    val DETAILED_INSTRUCTION = """
        Donne maintenant une réponse DÉTAILLÉE (8 à 15 phrases) qui développe ta
        réponse précédente : contexte, habitat, comportement, alimentation, statut
        de conservation, faits notables. Reste rigoureux et concret.
        Cite les articles [1], [2] etc. quand pertinent.
    """.trimIndent()

    fun withArticles(animal: String, abstracts: String): String = buildString {
        appendLine(BASE)
        appendLine()
        appendLine("Animal du contexte : $animal")
        if (abstracts.isNotBlank()) {
            appendLine()
            appendLine("Extraits de sources scientifiques (à utiliser en priorité) :")
            appendLine("---")
            appendLine(abstracts.take(8000))
            appendLine("---")
        }
    }
}
