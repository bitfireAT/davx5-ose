/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class WebDavDocumentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    private val documentDao by lazy { db.webDavDocumentDao() }
    private val mounts = mutableListOf<WebDavMount>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() = runBlocking {
        for (mount in mounts)
            db.webDavMountDao().delete(mount)
    }


    @Test
    fun testToKtorUrl_root() = runTest {
        val root = documentDao.getOrCreateRoot(insertMount("https://mock.example.com/webdav/"))

        val url = root.toKtorUrl(db)

        assertEquals("https://mock.example.com/webdav/", url.toString())
    }

    @Test
    fun testToKtorUrl_root_mountWithoutSlash() = runTest {
        val root = documentDao.getOrCreateRoot(insertMount("https://mock.example.com/webdav"))

        val url = root.toKtorUrl(db)

        assertEquals("https://mock.example.com/webdav/", url.toString())
    }

    @Test
    fun testToKtorUrl_root_mountWithEmptyPath() = runTest {
        val root = documentDao.getOrCreateRoot(insertMount("https://mock.example.com"))

        val url = root.toKtorUrl(db)

        assertEquals("https://mock.example.com/", url.toString())
    }

    @Test
    fun testToKtorUrl_file() = runTest {
        val root = documentDao.getOrCreateRoot(insertMount("https://mock.example.com/webdav/"))
        val folder = insertDocument(root, "folder", isDirectory = true)
        val file = insertDocument(folder, "file.txt", isDirectory = false)

        val url = file.toKtorUrl(db)

        assertEquals("https://mock.example.com/webdav/folder/file.txt", url.toString())
    }

    @Test
    fun testToKtorUrl_file_mountWithoutSlash() = runTest {
        val root = documentDao.getOrCreateRoot(insertMount("https://mock.example.com/webdav"))
        val file = insertDocument(root, "file.txt", isDirectory = false)

        val url = file.toKtorUrl(db)

        assertEquals("https://mock.example.com/webdav/file.txt", url.toString())
    }

    @Test
    fun testToKtorUrl_folder() = runTest {
        val root = documentDao.getOrCreateRoot(insertMount("https://mock.example.com/webdav/"))
        val folder = insertDocument(root, "folder", isDirectory = true)
        val sub = insertDocument(folder, "sub", isDirectory = true)

        val url = sub.toKtorUrl(db)

        assertEquals("https://mock.example.com/webdav/folder/sub/", url.toString())
    }

    @Test
    fun testToKtorUrl_notStored() = runTest {
        val root = documentDao.getOrCreateRoot(insertMount("https://mock.example.com/webdav/"))
        val folder = insertDocument(root, "folder", isDirectory = true)
        val newDoc = WebDavDocument(mountId = folder.mountId, parentId = folder.id, name = "new", isDirectory = true)

        val url = newDoc.toKtorUrl(db)

        assertEquals("https://mock.example.com/webdav/folder/new/", url.toString())
    }

    @Test
    fun testToKtorUrl_encodesName() = runTest {
        val root = documentDao.getOrCreateRoot(insertMount("https://mock.example.com/webdav/"))
        val folder = insertDocument(root, "a b#c", isDirectory = true)

        val url = folder.toKtorUrl(db)

        assertEquals("https://mock.example.com/webdav/a%20b%23c/", url.toString())
    }


    // helpers

    private suspend fun insertMount(url: String): WebDavMount {
        val mountDao = db.webDavMountDao()
        val id = mountDao.insert(WebDavMount(name = "Test Mount", url = Url(url)))
        return mountDao.getById(id).also { mounts += it }
    }

    private suspend fun insertDocument(parent: WebDavDocument, name: String, isDirectory: Boolean): WebDavDocument {
        val id = documentDao.insert(
            WebDavDocument(mountId = parent.mountId, parentId = parent.id, name = name, isDirectory = isDirectory)
        )
        return documentDao.get(id)!!
    }

}
