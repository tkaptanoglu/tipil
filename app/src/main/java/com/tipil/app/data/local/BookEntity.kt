package com.tipil.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "books")
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
    val addedAt: Long = System.currentTimeMillis()
)

class StringListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split("||")
    }

    @TypeConverter
    fun toString(list: List<String>): String {
        return list.joinToString("||")
    }
}
