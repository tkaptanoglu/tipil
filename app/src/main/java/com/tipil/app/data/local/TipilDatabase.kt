package com.tipil.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class TipilDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add indices that were missing in v1
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_userId` ON `books` (`userId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_userId_isbn` ON `books` (`userId`, `isbn`)")
            }
        }
    }
}
