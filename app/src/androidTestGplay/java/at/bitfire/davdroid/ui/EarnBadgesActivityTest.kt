/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.content.Context
import androidx.test.core.app.launchActivity
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.settings.SettingsManager
import com.google.android.play.core.review.testing.FakeReviewManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EarnBadgesActivityTest : KoinComponent {

    companion object {
        val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val _settings by inject<SettingsManager>()
    private var settings = spyk(_settings)

    @Test
    fun testShowRatingRequest() {
        launchActivity<EarnBadgesActivity>().use { scenario ->
            scenario.onActivity { activity ->
                val fakeReviewManager = spyk(FakeReviewManager(activity))
                activity.showRatingRequest(fakeReviewManager)
                verify {
                    fakeReviewManager.requestReviewFlow()
                }
            }
        }
    }

    @Test
    fun testShouldShowRatingRequest_firstInstallTimeIntervalPassed() {
        // Interval is two weeks
        mockkObject(EarnBadgesActivity) {
            every { EarnBadgesActivity.currentTime() } returns 1652343892058 // "now" = 12.05.22
            every { EarnBadgesActivity.installTime(targetContext) } returns 1651087147000 // "15 days ago" = 27.04.2022
            every { settings.getLongOrNull(EarnBadgesActivity.LAST_REVIEW_PROMPT) } returns 0 // "never" = 0
            assertTrue(EarnBadgesActivity.shouldShowRatingRequest(targetContext, settings)) // 15 > 14 => true
        }
    }

    @Test
    fun testShouldShowRatingRequest_firstInstallTimeIntervalNotPassed() {
        // Interval is two weeks
        mockkObject(EarnBadgesActivity) {
            every { EarnBadgesActivity.currentTime() } returns 1652306400000 // "now" = 12.05.22
            every { EarnBadgesActivity.installTime(targetContext) } returns 1652133600000 // "2 days ago" = 10.05.2022
            every { settings.getLongOrNull(EarnBadgesActivity.LAST_REVIEW_PROMPT) } returns 0 // "never" = 0
            assertFalse(EarnBadgesActivity.shouldShowRatingRequest(targetContext, settings)) // 2 > 14 => false
        }
    }

    @Test
    fun testShouldShowRatingRequest_firstInstallTimeAndLastReviewPromptIntervalPassed() {
        // Interval is two weeks
        mockkObject(EarnBadgesActivity) {
            every { EarnBadgesActivity.currentTime() } returns 1652306400000 // "now" = 12.05.22
            every { EarnBadgesActivity.installTime(targetContext) } returns 1651087147000 // "15 days ago" = 27.04.2022
            every { settings.getLongOrNull(EarnBadgesActivity.LAST_REVIEW_PROMPT) } returns 1651087147000 // "15 days ago" = 27.04.2022
            assertTrue(EarnBadgesActivity.shouldShowRatingRequest(targetContext, settings)) // 15 > 14 => true
        }
    }

    @Test
    fun testShouldShowRatingRequest_lastReviewPromptIntervalNotPassed() {
        // Interval is two weeks
        mockkObject(EarnBadgesActivity) {
            every { EarnBadgesActivity.currentTime() } returns 1652306400000 // "now" = 12.05.22
            every { EarnBadgesActivity.installTime(targetContext) } returns 1652343892058 // "15 days ago" = 27.04.2022
            every { settings.getLongOrNull(EarnBadgesActivity.LAST_REVIEW_PROMPT) } returns 1652306400000 // "now" = 12.05.22
            assertFalse(EarnBadgesActivity.shouldShowRatingRequest(targetContext, settings)) // 0 > 14 => false
        }
    }
}