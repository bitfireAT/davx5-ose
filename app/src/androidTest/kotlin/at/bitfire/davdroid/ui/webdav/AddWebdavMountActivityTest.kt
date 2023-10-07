/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.webdav

import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavMount
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.spyk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AddWebdavMountActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Before
    fun setUp() {
        hiltRule.inject()

        model = spyk(AddWebdavMountActivity.Model(InstrumentationRegistry.getInstrumentation().targetContext, db))

        Assume.assumeTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }


    lateinit var model: AddWebdavMountActivity.Model
    val web = MockWebServer()

    @Test
    fun testHasWebDav_NoDavHeader() {
        web.enqueue(MockResponse().setResponseCode(200))
        assertFalse(model.hasWebDav(WebDavMount(name = "Test", url = web.url("/")), null))
    }

    @Test
    fun testHasWebDav_DavClass_1() {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV", "1"))
        assertTrue(model.hasWebDav(WebDavMount(name = "Test", url = web.url("/")), null))
    }

    @Test
    fun testHasWebDav_DavClass_1and2() {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV", "1,2"))
        assertTrue(model.hasWebDav(WebDavMount(name = "Test", url = web.url("/")), null))
    }

    @Test
    fun testHasWebDav_DavClass_2() {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV", "2"))
        assertTrue(model.hasWebDav(WebDavMount(name = "Test", url = web.url("/")), null))
    }

}