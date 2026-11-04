/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.debuginfo

import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.log.DebugDirectory
import at.bitfire.davdroid.sync.ResourceRetriever
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class DebugInfoViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var debugDirectory: DebugDirectory

    @Inject
    lateinit var debugInfoGenerator: DebugInfoGenerator

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var logger: Logger

    private lateinit var accountId: LegacyAccount

    private val resourceRetriever = mockk<ResourceRetriever>()
    private val resourceRetrieverFactory = mockk<ResourceRetriever.Factory> {
        every { create(any(), any(), any()) } returns resourceRetriever
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        accountId = LegacyAccount(TestAccount.create(accountName = "DebugInfoViewModelTest"))
    }

    @After
    fun tearDown() {
        TestAccount.remove(accountId.androidAccount)
    }


    @Test
    fun testCanViewRemoteResource_File() {
        val model = createModel(remoteResource = "https://example.com/calendar/event.ics")

        assertTrue(model.canViewRemoteResource)
    }

    @Test
    fun testCanViewRemoteResource_Collection() {
        val model = createModel(remoteResource = "https://example.com/calendar/")

        assertFalse(model.canViewRemoteResource)
    }

    @Test
    fun testCanViewRemoteResource_NoAccount() {
        val model = createModel(remoteResource = "https://example.com/calendar/event.ics", withAccount = false)

        assertFalse(model.canViewRemoteResource)
    }

    @Test
    fun testCanViewRemoteResource_NoRemoteResource() {
        val model = createModel(remoteResource = null)

        assertFalse(model.canViewRemoteResource)
    }

    @Test
    fun testDownloadRemoteResource_Success() = runTest {
        coEvery { resourceRetriever.retrieve("https://example.com/calendar/event.ics") } returns "BEGIN:VCALENDAR".toByteArray()
        val model = createModel(remoteResource = "https://example.com/calendar/event.ics")

        model.downloadRemoteResource()
        advanceUntilIdle()

        verify { resourceRetrieverFactory.create(accountId, "example.com", any()) }
        assertEquals("BEGIN:VCALENDAR", model.uiState.remoteResourceFile?.readText())
        assertFalse(model.uiState.remoteResourceInProgress)
    }

    @Test
    fun testDownloadRemoteResource_Failure() = runTest {
        coEvery { resourceRetriever.retrieve(any()) } returns null
        val model = createModel(remoteResource = "https://example.com/calendar/event.ics")

        model.downloadRemoteResource()
        advanceUntilIdle()

        assertNull(model.uiState.remoteResourceFile)
        assertEquals(context.getString(R.string.debug_info_can_not_view_resource), model.uiState.error)
        assertFalse(model.uiState.remoteResourceInProgress)
    }


    private fun createModel(remoteResource: String?, withAccount: Boolean = true) =
        DebugInfoViewModel(
            details = DebugInfoViewModel.DebugInfoDetails(
                accountId = if (withAccount) accountId else null,
                syncDataType = null,
                cause = null,
                localResource = null,
                remoteResource = remoteResource,
                timestamp = null
            ),
            context = context,
            debugDirectory = debugDirectory,
            debugInfoGenerator = debugInfoGenerator,
            ioDispatcher = ioDispatcher,
            logger = logger,
            resourceRetrieverFactory = resourceRetrieverFactory
        )

}
