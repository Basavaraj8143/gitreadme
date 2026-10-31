package com.byteharvest.mdreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val LOCAL_REPOSITORY_ID = "local"

/** id = "owner/name@branch" (owner/name lower-cased), so branches of one repo never mix. */
@Entity(tableName = "repositories")
data class RepositoryEntity(
    @PrimaryKey val id: String,
    val owner: String,
    val name: String,
    val branch: String,
    val description: String?,
    val isPrivate: Boolean,
    val addedAt: Long,
    val lastOpenedAt: Long,
    val lastSyncAt: Long?,
    val syncState: String, // OK | PARTIAL | FAILED
)

@Entity(
    tableName = "tree_entries",
    primaryKeys = ["repoId", "path"],
    foreignKeys = [ForeignKey(RepositoryEntity::class, ["id"], ["repoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("repoId")],
)
data class TreeEntryEntity(val repoId: String, val path: String, val sha: String)

@Entity(
    tableName = "cached_documents",
    primaryKeys = ["repoId", "path"],
    foreignKeys = [ForeignKey(RepositoryEntity::class, ["id"], ["repoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("repoId")],
)
data class CachedDocumentEntity(
    val repoId: String,
    val path: String,
    val sha: String,
    val content: String,
    val cachedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    primaryKeys = ["repoId", "path"],
    foreignKeys = [ForeignKey(RepositoryEntity::class, ["id"], ["repoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("repoId")],
)
data class BookmarkEntity(val repoId: String, val path: String, val title: String, val createdAt: Long)

@Entity(
    tableName = "reading_history",
    primaryKeys = ["repoId", "path"],
    foreignKeys = [ForeignKey(RepositoryEntity::class, ["id"], ["repoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("repoId")],
)
data class HistoryEntity(val repoId: String, val path: String, val title: String, val lastOpenedAt: Long)

data class DocMeta(val path: String, val sha: String)

data class RecentDoc(
    val repoId: String, val path: String, val title: String, val lastOpenedAt: Long,
    val owner: String, val repoName: String, val branch: String,
)

data class BookmarkItem(
    val repoId: String, val path: String, val title: String, val createdAt: Long,
    val owner: String, val repoName: String, val branch: String,
)

data class OfflineRepositoryItem(
    val repoId: String,
    val owner: String,
    val repoName: String,
    val branch: String,
    val description: String?,
    val totalDocs: Int,
    val cachedDocs: Int,
)
