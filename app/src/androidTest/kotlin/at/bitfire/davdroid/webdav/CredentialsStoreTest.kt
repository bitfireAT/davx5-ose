/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.db.Credentials
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
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
    lateinit var store: CredentialsStore

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testSetGetDelete() = runTest {
        store.setCredentials(0, Credentials(username = "myname", password = "12345"))
        assertEquals(Credentials(username = "myname", password = "12345"), store.getCredentials(0))

        store.setCredentials(0, null)
        assertNull(store.getCredentials(0))
    }

}