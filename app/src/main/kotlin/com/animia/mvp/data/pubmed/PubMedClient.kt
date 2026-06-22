package com.animia.mvp.data.pubmed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object PubMedClient {

    private const val BASE_URL = "https://eutils.ncbi.nlm.nih.gov/"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: PubMedApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PubMedApi::class.java)
    }

    /** Parse manuellement la réponse esummary (structure dynamique avec ids comme clés). */
    fun parseSummary(response: PubMedSummaryResponse): List<PubMedArticle> {
        val root = response.result.jsonObject
        val uids = root["uids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        return uids.mapNotNull { uid ->
            val obj = root[uid]?.jsonObject ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.cleanHtml() ?: return@mapNotNull null
            val journal = obj["fulljournalname"]?.jsonPrimitive?.contentOrNull
                ?: obj["source"]?.jsonPrimitive?.contentOrNull
                ?: ""
            val pubDate = obj["pubdate"]?.jsonPrimitive?.contentOrNull ?: ""
            val year = pubDate.take(4)
            val authors = obj["authors"]?.jsonArray?.take(3)?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            }?.joinToString(", ") ?: ""
            PubMedArticle(
                pmid = uid,
                title = title,
                authors = authors,
                journal = journal,
                year = year,
                url = "https://pubmed.ncbi.nlm.nih.gov/$uid/"
            )
        }
    }

    /** Découpe le bloc texte renvoyé par efetch en un abstract par article. */
    fun splitAbstracts(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        // Les articles sont séparés par des lignes vides multiples
        return text.split(Regex("\\n\\s*\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun String.cleanHtml(): String = this
        .replace(Regex("<[^>]+>"), "")
        .trim()
}
