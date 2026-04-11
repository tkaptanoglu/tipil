package com.tipil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isbn"], unique = true)
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
    val addedAt: Long = 0  // set explicitly at insertion time
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
