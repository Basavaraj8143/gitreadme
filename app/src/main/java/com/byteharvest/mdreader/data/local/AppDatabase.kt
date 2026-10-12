package com.byteharvest.mdreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// Schema JSON is exported to app/schemas. Future schema changes MUST bump `version` and add a Migration;
// the app never uses fallbackToDestructiveMigration.
@Database(
    entities = [
        RepositoryEntity::class, TreeEntryEntity::class, CachedDocumentEntity::class,
        BookmarkEntity::class, HistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}
