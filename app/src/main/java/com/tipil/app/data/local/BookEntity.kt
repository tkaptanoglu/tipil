package com.tipil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * A single item in the user's library — book, CD, DVD, magazine, or board game.
 *
 * Shared columns cover the common fields across all media types:
 *   title, authors (or artists/directors), genres, coverUrl, etc.
 *
 * Type-specific data (track list for CDs, player count for board games, etc.)
 * lives in [mediaMetadata] as a JSON string, decoded per [mediaType].
 *
 * The [isbn] column holds any barcode identifier: ISBN-10/13 for books,
 * UPC/EAN for other media.  The unique index on (userId, isbn) prevents
 * the same physical item from being added twice.
 *
 * The [isRead] column is reused across all types to mean "consumed/experienced"
 * (read a book, listened to a CD, watched a DVD, played a game).
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isbn"], unique = true),
        Index(value = ["userId", "mediaType"])
    ]
)
@TypeConverters(StringListConverter::class)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val isbn: String,
    val title: String,
    val subtitle: String = "",
    val authors: String,
    val publisher: String = "",
    val editor: String = "",
    val publishedYear: String = "",
    val pageCount: Int = 0,
    val isFiction: Boolean = true,
    val genres: List<String> = emptyList(),
    val coverUrl: String = "",
    val description: String = "",
    val isRead: Boolean = false,
    val addedAt: Long = 0,  // set explicitly at insertion time
    val mediaType: String = MediaType.BOOK.name,
    val mediaMetadata: String = ""  // JSON blob for type-specific fields
)

class StringListConverter {
    @TypeConverter
    fun toList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split("||")
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString("||")
    }
}
