package com.byteharvest.mdreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

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

    companion object {
        val MIGRATIONS: List<Migration> = emptyList()

        /** Builds the database with migrations. Never uses fallbackToDestructiveMigration. */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mdreader.db"
            )
                .addMigrations(*MIGRATIONS.toTypedArray())
                .build()
    }
}
