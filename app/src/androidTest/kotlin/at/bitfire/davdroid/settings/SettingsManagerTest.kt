/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import androidx.lifecycle.Observer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }


    @Test
    fun testContainsKey_NotExisting() {
        assertFalse(settingsManager.containsKey("notExisting"))
    }

    @Test
    fun testContainsKey_Existing() {
        // provided by DefaultsProvider
        assertEquals(Settings.PROXY_TYPE_SYSTEM, settingsManager.getInt(Settings.PROXY_TYPE))
    }

    @Test
    fun testObserver() {
        var value = -1
        val observer = SettingsManager.OnChangeListener {
            value = settingsManager.getInt("test")
        }
        settingsManager.addOnChangeListener(observer)
        settingsManager.putInt("test", 1)
        // wait for observer to be called
        runBlocking {
            withTimeout(1000) {
                while (value == -1) {
                    delay(10)
                }
            }
        }
        // make sure the value was updated
        assertEquals(1, value)
        settingsManager.removeOnChangeListener(observer)
    }

    @Test
    fun test_getBooleanLive() = runBlocking(Dispatchers.Main) {
        val live = settingsManager.getBooleanLive("test")

        // it's required to add an observer, otherwise onActive is not called
        val observer = Observer<Boolean?> { /* nothing required */ }
        live.observeForever(observer)

        assertNull(live.value)

        settingsManager.putBoolean("test", true)
        runBlocking {
            withTimeout(1000) {
                while (live.value != true) {
                    delay(10)
                }
            }
        }
        assertTrue(live.value ?: false)
        assertTrue(settingsManager.getBoolean("test"))

        live.value = false
        assertFalse(live.value ?: true)
        assertFalse(settingsManager.getBoolean("test"))
    }
}