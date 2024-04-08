/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SettingsManagerTest {

    companion object {
        /** Use this setting to test SettingsManager methods. Will be removed after every test run. */
        const val SETTING_TEST = "test"
    }


    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Inject lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @After
    fun removeTestSetting() {
        settingsManager.remove(SETTING_TEST)
    }


    @Test
    fun test_containsKey_NotExisting() {
        assertFalse(settingsManager.containsKey("notExisting"))
    }

    @Test
    fun test_containsKey_Existing() {
        // provided by DefaultsProvider
        assertEquals(Settings.PROXY_TYPE_SYSTEM, settingsManager.getInt(Settings.PROXY_TYPE))
    }


    /*@Test
    fun test_observerFlow_initialValue() = runBlocking {
        var counter = 0
        val live = settingsManager.observerFlow {
            if (counter++ == 0)
                23
            else
                throw AssertionError("A second value was requested")
        }
        assertEquals(23, live.singleOrNull())
    }

    @Test
    fun test_observerFlow_updatedValue() = runBlocking {
        var counter = 0
        val live = settingsManager.observerFlow {
            when (counter++) {
                0 -> 23     // initial value
                1 -> 42     // updated value
                else -> throw AssertionError()
            }
        }

        var collectCounter = 0
        live.collect {
            when (collectCounter++) {
                0 -> {
                    assertEquals(23, it)
                    // first value collected, send some update so that onChange listener is triggered
                    settingsManager.putBoolean(SETTING_TEST, true)
                }
                1 -> {
                    assertEquals(42, it)
                    // second value collected, success, stop here
                    cancel()
                }
                else -> throw AssertionError()
            }
        }
        // two values should have been collected
        assertEquals(2, collectCounter)
    }*/

}