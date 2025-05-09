/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.rule.ServiceTestRule
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class UnifiedPushServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @get:Rule
    val serviceTestRule = ServiceTestRule()

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @RelaxedMockK
    @BindValue
    lateinit var pushRegistrationManager: PushRegistrationManager

    lateinit var binder: IBinder
    lateinit var unifiedPushService: UnifiedPushService


    @Before
    fun setUp() {
        hiltRule.inject()

        binder = serviceTestRule.bindService(Intent(context, UnifiedPushService::class.java))!!
        unifiedPushService = (binder as PushService.PushBinder).getService() as UnifiedPushService
    }


    @Test
    fun testOnNewEndpoint() = runTest {
        val endpoint = mockk<PushEndpoint> {
            every { url } returns "https://example.com/12"
        }
        unifiedPushService.onNewEndpoint(endpoint, "12")

        advanceUntilIdle()
        coVerify {
            pushRegistrationManager.processSubscription(12, endpoint)
        }
        confirmVerified(pushRegistrationManager)
    }

    @Test
    fun testOnRegistrationFailed() = runTest {
        unifiedPushService.onRegistrationFailed(FailedReason.INTERNAL_ERROR, "34")

        advanceUntilIdle()
        coVerify {
            pushRegistrationManager.removeSubscription(34)
        }
        confirmVerified(pushRegistrationManager)
    }

    @Test
    fun testOnUnregistered() = runTest {
        unifiedPushService.onRegistrationFailed(FailedReason.INTERNAL_ERROR, "45")

        advanceUntilIdle()
        coVerify {
            pushRegistrationManager.removeSubscription(45)
        }
        confirmVerified(pushRegistrationManager)
    }

}