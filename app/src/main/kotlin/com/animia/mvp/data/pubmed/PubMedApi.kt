package com.animia.mvp.data.pubmed

import retrofit2.http.GET
import retrofit2.http.Query

interface PubMedApi {

    @GET("entrez/eutils/esearch.fcgi")
    suspend fun search(
        @Query("db") db: String = "pubmed",
        @Query("term") term: String,
        @Query("retmax") retMax: Int = 30,
        @Query("retmode") retMode: String = "json",
        @Query("sort") sort: String = "relevance"
    ): PubMedSearchResponse

    @GET("entrez/eutils/esummary.fcgi")
    suspend fun summary(
        @Query("db") db: String = "pubmed",
        @Query("id") ids: String,
        @Query("retmode") retMode: String = "json"
    ): PubMedSummaryResponse

    @GET("entrez/eutils/efetch.fcgi")
    suspend fun fetchAbstracts(
        @Query("db") db: String = "pubmed",
        @Query("id") ids: String,
        @Query("rettype") retType: String = "abstract",
        @Query("retmode") retMode: String = "text"
    ): String
}
