/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.settings.Credentials
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CredentialsStoreTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var store: LegacyCredentialsStore

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testSetGetDelete() {
        store.setCredentials(0, Credentials(username = "myname", password = "12345".toCharArray()))
        assertEquals(Credentials(username = "myname", password = "12345".toCharArray()), store.getCredentials(0))

        store.setCredentials(0, null)
        assertNull(store.getCredentials(0))
    }

}