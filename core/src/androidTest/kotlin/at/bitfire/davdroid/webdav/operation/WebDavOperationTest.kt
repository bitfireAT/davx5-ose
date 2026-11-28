/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Base for WebDAV operation tests: mount at `https://mock.example.com/webdav/` and a [mockEngine] answering with [handler].
 * Subclasses need `@BindValue @JvmField val httpClientBuilder = mockHttpClientBuilder()` (Hilt allows `@BindValue` only there).
 */
abstract class WebDavOperationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    /** Answers requests to [mockEngine]; defaults to 201 Created. */
    var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond("", HttpStatusCode.Created)
    }

    val mockEngine = MockEngine { request -> handler(request) }

    val documentDao by lazy { db.webDavDocumentDao() }

    lateinit var root: WebDavDocument

    @Before
    fun setUpWebDav() {
        hiltRule.inject()

        runBlocking {
            val mountDao = db.webDavMountDao()
            val mountId = mountDao.insert(WebDavMount(name = "Test Mount", url = Url("https://mock.example.com/webdav/")))
            root = documentDao.getOrCreateRoot(mountDao.getById(mountId))
        }
    }

    fun mockHttpClientBuilder(): DavHttpClientBuilder = mockk {
        coEvery { build(any(), any()) } answers { HttpClient(mockEngine) }
    }

    suspend fun insertDocument(parent: WebDavDocument, name: String, isDirectory: Boolean, displayName: String? = null): WebDavDocument {
        val id = documentDao.insert(
            WebDavDocument(mountId = parent.mountId, parentId = parent.id, name = name, isDirectory = isDirectory, displayName = displayName)
        )
        return documentDao.get(id)!!
    }

}
