package com.tipil.app.data.remote

import com.google.gson.annotations.SerializedName

/** Open Library edition response from /isbn/{isbn}.json */
data class OlEdition(
    @SerializedName("title") val title: String = "",
    @SerializedName("subtitle") val subtitle: String? = null,
    @SerializedName("authors") val authors: List<OlAuthorRef>? = null,
    @SerializedName("publishers") val publishers: List<String>? = null,
    @SerializedName("publish_date") val publishDate: String? = null,
    @SerializedName("number_of_pages") val numberOfPages: Int? = null,
    @SerializedName("covers") val covers: List<Long>? = null,
    @SerializedName("isbn_13") val isbn13: List<String>? = null,
    @SerializedName("isbn_10") val isbn10: List<String>? = null,
    @SerializedName("subjects") val subjects: List<String>? = null,
    @SerializedName("description") val description: Any? = null,
    @SerializedName("works") val works: List<OlWorkRef>? = null
)

data class OlAuthorRef(
    @SerializedName("key") val key: String = ""
)

data class OlWorkRef(
    @SerializedName("key") val key: String = ""
)

/** Open Library work response from /works/{id}.json */
data class OlWork(
    @SerializedName("title") val title: String = "",
    @SerializedName("subjects") val subjects: List<String>? = null,
    @SerializedName("description") val description: Any? = null
)

/** Open Library author response from /authors/{id}.json */
data class OlAuthor(
    @SerializedName("name") val name: String = "",
    @SerializedName("personal_name") val personalName: String? = null
)

/** Open Library search response */
data class OlSearchResponse(
    @SerializedName("numFound") val numFound: Int = 0,
    @SerializedName("docs") val docs: List<OlSearchDoc>? = null
)

data class OlSearchDoc(
    @SerializedName("title") val title: String = "",
    @SerializedName("author_name") val authorName: List<String>? = null,
    @SerializedName("isbn") val isbn: List<String>? = null,
    @SerializedName("cover_i") val coverId: Long? = null,
    @SerializedName("subject") val subjects: List<String>? = null
)
