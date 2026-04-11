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
    BOARD_GAME("Board Game");

    companion object {
        /** Safe parse that falls back to BOOK for unknown/corrupt values. */
        fun fromName(name: String): MediaType =
            try { valueOf(name) } catch (_: IllegalArgumentException) { BOOK }
    }
}
