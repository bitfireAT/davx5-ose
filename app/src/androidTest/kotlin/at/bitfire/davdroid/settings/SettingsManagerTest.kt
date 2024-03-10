/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import at.bitfire.davdroid.TestUtils.getOrAwaitValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun test_getBooleanLive_getValue() {
        val live = settingsManager.getBooleanLive(SETTING_TEST)
        assertNull(live.value)

        // posts value to main thread, InstantTaskExecutorRule is required to execute it instantly
        settingsManager.putBoolean(SETTING_TEST, true)
        runBlocking(Dispatchers.Main) {     // observeForever can't be run in background thread
            assertTrue(live.getOrAwaitValue())
        }
    }


    @Test
    fun test_ObserverCalledWhenValueChanges() {
        val value = CompletableDeferred<Int>()
        val observer = SettingsManager.OnChangeListener {
            value.complete(settingsManager.getInt(SETTING_TEST))
        }

        try {
            settingsManager.addOnChangeListener(observer)
            settingsManager.putInt(SETTING_TEST, 123)

            runBlocking {
                // wait until observer is called
                assertEquals(123, value.await())
            }

        } finally {
            settingsManager.removeOnChangeListener(observer)
        }
    }

}