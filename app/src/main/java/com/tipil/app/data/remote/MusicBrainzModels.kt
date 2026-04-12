package com.tipil.app.data.remote

import com.google.gson.annotations.SerializedName

/** Top-level search response from /ws/2/release/?query=barcode:... */
data class MusicBrainzSearchResponse(
    @SerializedName("count") val count: Int = 0,
    @SerializedName("releases") val releases: List<MbRelease>? = null
)

data class MbRelease(
    @SerializedName("id") val id: String = "",
    @SerializedName("score") val score: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("status") val status: String? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("artist-credit") val artistCredit: List<MbArtistCredit>? = null,
    @SerializedName("release-group") val releaseGroup: MbReleaseGroupRef? = null,
    @SerializedName("label-info") val labelInfo: List<MbLabelInfo>? = null,
    @SerializedName("track-count") val trackCount: Int = 0,
    @SerializedName("media") val media: List<MbMedia>? = null
)

data class MbArtistCredit(
    @SerializedName("name") val name: String = "",
    @SerializedName("artist") val artist: MbArtist? = null,
    @SerializedName("joinphrase") val joinPhrase: String? = null
)

data class MbArtist(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = ""
)

data class MbReleaseGroupRef(
    @SerializedName("id") val id: String = "",
    @SerializedName("primary-type") val primaryType: String? = null,
    @SerializedName("title") val title: String? = null
)

data class MbLabelInfo(
    @SerializedName("catalog-number") val catalogNumber: String? = null,
    @SerializedName("label") val label: MbLabel? = null
)

data class MbLabel(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = ""
)

data class MbMedia(
    @SerializedName("format") val format: String? = null,
    @SerializedName("track-count") val trackCount: Int = 0
)

/** Lookup response from /ws/2/release-group/{id}?inc=tags */
data class MbReleaseGroupDetail(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("primary-type") val primaryType: String? = null,
    @SerializedName("tags") val tags: List<MbTag>? = null
)

data class MbTag(
    @SerializedName("name") val name: String = "",
    @SerializedName("count") val count: Int = 0
)
