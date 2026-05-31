package com.tipil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "not_found_scans",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "barcode"], unique = true)
    ]
)
data class NotFoundScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val barcode: String,
    val mediaType: String = MediaType.BOOK.name,
    val scannedAt: Long = System.currentTimeMillis()
)
