package com.tipil.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, NotFoundScanEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class TipilDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun notFoundScanDao(): NotFoundScanDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_userId` ON `books` (`userId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_userId_isbn` ON `books` (`userId`, `isbn`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add mediaType column — all existing rows are books
                db.execSQL("ALTER TABLE `books` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'BOOK'")
                // Add mediaMetadata JSON blob column
                db.execSQL("ALTER TABLE `books` ADD COLUMN `mediaMetadata` TEXT NOT NULL DEFAULT ''")
                // Index for filtering by user + mediaType
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_userId_mediaType` ON `books` (`userId`, `mediaType`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `not_found_scans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `barcode` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL DEFAULT 'BOOK',
                        `scannedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_not_found_scans_userId` ON `not_found_scans` (`userId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_not_found_scans_userId_barcode` ON `not_found_scans` (`userId`, `barcode`)")
            }
        }
    }
}
