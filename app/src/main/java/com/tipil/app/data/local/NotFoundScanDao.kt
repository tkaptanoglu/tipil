package com.tipil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotFoundScanDao {

    @Query("SELECT * FROM not_found_scans WHERE userId = :userId ORDER BY scannedAt DESC")
    fun getByUser(userId: String): Flow<List<NotFoundScanEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scan: NotFoundScanEntity)

    @Query("DELETE FROM not_found_scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM not_found_scans WHERE userId = :userId AND barcode = :barcode")
    suspend fun deleteByBarcode(userId: String, barcode: String)

    /** Empties the whole queue for one user. Scoped by userId, never global. */
    @Query("DELETE FROM not_found_scans WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
