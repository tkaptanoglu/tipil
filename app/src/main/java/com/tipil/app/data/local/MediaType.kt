package com.tipil.app.data.local

/**
 * The type of collectible item in the library.
 *
 * Each enum name is stored as a TEXT column in Room, so names must remain
 * stable across app versions. Add new types at the end only.
 */
enum class MediaType(val label: String) {
    BOOK("Book"),
    CD("CD"),
    CASSETTE("Cassette"),
    DVD("DVD"),
    MAGAZINE("Magazine"),
    BOARD_GAME("Board Game"),
    VINYL("Vinyl");

    /** The high-level library category this type belongs to. */
    val category: MediaCategory
        get() = when (this) {
            BOOK -> MediaCategory.BOOKS
            CD, CASSETTE, VINYL -> MediaCategory.MUSIC
            DVD -> MediaCategory.DVDS
            MAGAZINE -> MediaCategory.MAGAZINES
            BOARD_GAME -> MediaCategory.BOARD_GAMES
        }

    /** True for CD, Cassette, Vinyl — all types that use MusicBrainz. */
    val isMusic: Boolean get() = category == MediaCategory.MUSIC

    companion object {
        /** Safe parse that falls back to BOOK for unknown/corrupt values. */
        fun fromName(name: String): MediaType =
            try { valueOf(name) } catch (_: IllegalArgumentException) { BOOK }

        /** All music-related media types. */
        val MUSIC_TYPES: Set<MediaType> = setOf(CD, CASSETTE, VINYL)
    }
}

/**
 * High-level grouping for library tabs.
 *
 * Multiple [MediaType]s map to a single category (e.g. CD, Cassette, Vinyl → MUSIC).
 * The library shows one tab per category, not per individual type.
 */
enum class MediaCategory(val label: String, val itemLabel: String, val pluralLabel: String) {
    BOOKS("Books", "book", "books"),
    MUSIC("Music", "album", "albums"),
    DVDS("DVDs", "DVD", "DVDs"),
    MAGAZINES("Magazines", "magazine", "magazines"),
    BOARD_GAMES("Board Games", "board game", "board games");

    /** The [MediaType]s that belong to this category. */
    val mediaTypes: Set<MediaType>
        get() = MediaType.entries.filter { it.category == this }.toSet()
}
