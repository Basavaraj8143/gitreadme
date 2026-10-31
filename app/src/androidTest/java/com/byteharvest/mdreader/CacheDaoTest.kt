package com.byteharvest.mdreader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteharvest.mdreader.data.local.AppDatabase
import com.byteharvest.mdreader.data.local.CachedDocumentEntity
import com.byteharvest.mdreader.data.local.BookmarkEntity
import com.byteharvest.mdreader.data.local.HistoryEntity
import com.byteharvest.mdreader.data.local.LOCAL_REPOSITORY_ID
import com.byteharvest.mdreader.data.local.RepositoryEntity
import com.byteharvest.mdreader.data.local.TreeEntryEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheDaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    private fun repo(id: String) = RepositoryEntity(id, "o", id, "main", null, false, 0, 0, null, "OK")

    @Test fun samePathInTwoReposDoesNotMix() = runBlocking {
        val dao = db.dao()
        dao.upsertRepo(repo("a")); dao.upsertRepo(repo("b"))
        dao.upsertDoc(CachedDocumentEntity("a", "README.md", "s1", "from A", 1))
        dao.upsertDoc(CachedDocumentEntity("b", "README.md", "s2", "from B", 1))
        assertEquals("from A", dao.doc("a", "README.md")?.content)
        assertEquals("from B", dao.doc("b", "README.md")?.content)
    }

    @Test fun upsertingRepoKeepsItsCachedDocuments() = runBlocking {
        val dao = db.dao()
        dao.upsertRepo(repo("a"))
        dao.upsertDoc(CachedDocumentEntity("a", "x.md", "s", "text", 1))
        dao.upsertRepo(repo("a").copy(description = "updated")) // must not cascade-delete children
        assertNotNull(dao.doc("a", "x.md"))
    }

    @Test fun deletingRepoRemovesOnlyItsData() = runBlocking {
        val dao = db.dao()
        dao.upsertRepo(repo("a")); dao.upsertRepo(repo("b"))
        dao.upsertDoc(CachedDocumentEntity("a", "x.md", "s", "A", 1))
        dao.upsertDoc(CachedDocumentEntity("b", "x.md", "s", "B", 1))
        dao.upsertTree(listOf(TreeEntryEntity("a", "x.md", "s")))
        dao.deleteRepo("a")
        assertNull(dao.doc("a", "x.md"))
        assertNull(dao.treeSha("a", "x.md"))
        assertEquals("B", dao.doc("b", "x.md")?.content)
    }

    @Test fun localFilesAppearInHistoryAndBookmarksButNotRepositoryList() = runBlocking {
        val dao = db.dao()
        val now = System.currentTimeMillis()
        dao.upsertRepo(
            RepositoryEntity(
                id = LOCAL_REPOSITORY_ID,
                owner = "On this device",
                name = "Local files",
                branch = "local",
                description = null,
                isPrivate = false,
                addedAt = now,
                lastOpenedAt = now,
                lastSyncAt = null,
                syncState = "OK",
            )
        )
        dao.upsertHistory(HistoryEntity(LOCAL_REPOSITORY_ID, "/private/local_docs/123_guide.md", "guide.md", now))
        dao.upsertBookmark(BookmarkEntity(LOCAL_REPOSITORY_ID, "/private/local_docs/123_guide.md", "guide.md", now))

        assertEquals(0, dao.repositories().first().size)
        assertEquals("On this device", dao.recent(10).first().single().owner)
        assertEquals("guide.md", dao.bookmarks().first().single().title)
    }
}
