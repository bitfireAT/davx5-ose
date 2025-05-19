/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class WebDavDocumentDaoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var logger: Logger

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testGetChildren() = runTest {
        val mountDao = db.webDavMountDao()
        val dao = db.webDavDocumentDao()

        val mount = WebDavMount(id = 1, name = "Test", url = "https://example.com/".toHttpUrl())
        db.webDavMountDao().insert(mount)

        val root = WebDavDocument(
            id = 1,
            mountId = mount.id,
            parentId = null,
            name = "Root Document"
        )
        dao.insertOrReplace(root)
        dao.insertOrReplace(WebDavDocument(id = 0, mountId = mount.id, parentId = root.id, name = "Name 1", displayName = "DisplayName 2"))
        dao.insertOrReplace(WebDavDocument(id = 0, mountId = mount.id, parentId = root.id, name = "Name 2", displayName = "DisplayName 1"))
        try {
            val result = dao.getChildren(root.id, orderBy = "name DESC")
            logger.log(Level.INFO, "getChildren Result", result)

            assertEquals(listOf(
                "Name 2",
                "Name 1"
            ), result.map { it.name })
        } finally {
            mountDao.deleteAsync(mount)
        }
    }

}