/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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


    @Test
    fun test_observerFlow_initialValue() = runBlocking {
        var counter = 0
        val live = settingsManager.observerFlow {
            if (counter++ == 0)
                23
            else
                throw AssertionError("A second value was requested")
        }
        assertEquals(23, live.first())
    }

    @Test
    fun test_observerFlow_updatedValue() = runBlocking {
        var counter = 0
        val live = settingsManager.observerFlow {
            when (counter++) {
                0 -> {
                    // update some setting so that we will be called a second time
                    settingsManager.putBoolean(SETTING_TEST, true)
                    // and emit initial value
                    23
                }
                1 -> 42     // updated value
                else -> throw AssertionError()
            }
        }

        val result = live.take(2).toList()
        assertEquals(listOf(23, 42), result)
    }

}