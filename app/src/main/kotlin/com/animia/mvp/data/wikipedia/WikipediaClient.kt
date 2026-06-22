package com.animia.mvp.data.wikipedia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class WikiSummary(
    val title: String? = null,
    val extract: String? = null,
    @SerialName("content_urls") val contentUrls: WikiContentUrls? = null,
    val thumbnail: WikiImage? = null,
    val originalimage: WikiImage? = null,
    val description: String? = null,
    val lang: String? = null
)

@Serializable
data class WikiContentUrls(
    val desktop: WikiUrlSet? = null,
    val mobile: WikiUrlSet? = null
)

@Serializable
data class WikiUrlSet(val page: String? = null)

@Serializable
data class WikiImage(
    val source: String,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class WikiSearchResponse(val query: WikiSearchQuery? = null)

@Serializable
data class WikiSearchQuery(val search: List<WikiSearchHit> = emptyList())

@Serializable
data class WikiSearchHit(val title: String)

data class WikiArticleData(
    val title: String,
    val extract: String,
    val url: String,
    val imageUrl: String?,
    val lang: String
)

interface WikipediaApi {
    @GET("api/rest_v1/page/summary/{title}")
    suspend fun summary(@Path("title") title: String): WikiSummary

    @GET("w/api.php?action=query&list=search&format=json&srlimit=3")
    suspend fun search(@Query("srsearch") query: String): WikiSearchResponse
}

object WikipediaClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val frApi: WikipediaApi = build("https://fr.wikipedia.org/")
    private val enApi: WikipediaApi = build("https://en.wikipedia.org/")
    private val apis = listOf("fr" to frApi, "en" to enApi)

    private fun build(baseUrl: String): WikipediaApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(WikipediaApi::class.java)

    /**
     * Cherche l'article Wikipédia avec extrait + image. Stratégie :
     * Pour chaque (nom, langue), essaie 1) titre direct, 2) search puis titre du 1er hit.
     */
    suspend fun fetchArticle(scientificName: String?, commonName: String?): WikiArticleData? {
        val terms = listOfNotNull(
            scientificName?.takeIf { it.isNotBlank() },
            commonName?.takeIf { it.isNotBlank() }
        )
        for (term in terms) {
            for ((lang, api) in apis) {
                tryFetch(api, term, lang)?.takeIf { it.extract.isNotBlank() }?.let { return it }
                val viaSearch = searchThenFetch(api, term, lang)
                if (viaSearch != null && viaSearch.extract.isNotBlank()) return viaSearch
            }
        }
        return null
    }

    /**
     * Trouve juste une image (l'extrait n'est PAS requis). Sert à toujours
     * pouvoir afficher l'animal en photo, même si l'article n'a pas de texte.
     */
    suspend fun findImage(scientificName: String?, commonName: String?): String? {
        val terms = listOfNotNull(
            scientificName?.takeIf { it.isNotBlank() },
            commonName?.takeIf { it.isNotBlank() }
        )
        for (term in terms) {
            for ((lang, api) in apis) {
                tryFetch(api, term, lang)?.imageUrl?.takeIf { it.isNotBlank() }?.let { return it }
                searchThenFetch(api, term, lang)?.imageUrl?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    private suspend fun tryFetch(api: WikipediaApi, term: String, lang: String): WikiArticleData? {
        val encoded = URLEncoder.encode(term.trim().replace(' ', '_'), "UTF-8")
            .replace("+", "%20")
        val summary = runCatching { api.summary(encoded) }.getOrNull() ?: return null
        return summary.toData(lang)
    }

    private suspend fun searchThenFetch(api: WikipediaApi, term: String, lang: String): WikiArticleData? {
        val res = runCatching { api.search(term) }.getOrNull() ?: return null
        val firstTitle = res.query?.search?.firstOrNull()?.title ?: return null
        return tryFetch(api, firstTitle, lang)
    }

    private fun WikiSummary.toData(lang: String): WikiArticleData? {
        val title = this.title ?: return null
        val image = originalimage?.source ?: thumbnail?.source
        val url = contentUrls?.desktop?.page
            ?: "https://$lang.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        return WikiArticleData(
            title = title,
            extract = extract?.trim().orEmpty(),
            url = url,
            imageUrl = image,
            lang = lang
        )
    }
}
