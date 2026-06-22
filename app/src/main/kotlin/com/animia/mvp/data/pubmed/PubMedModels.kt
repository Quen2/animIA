package com.animia.mvp.data.pubmed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PubMedSearchResponse(
    val esearchresult: ESearchResult
)

@Serializable
data class ESearchResult(
    val count: String = "0",
    val idlist: List<String> = emptyList()
)

@Serializable
data class PubMedSummaryResponse(
    val result: JsonElement
)

data class PubMedArticle(
    val pmid: String,
    val title: String,
    val authors: String,
    val journal: String,
    val year: String,
    val url: String,
    val abstractText: String? = null
)
