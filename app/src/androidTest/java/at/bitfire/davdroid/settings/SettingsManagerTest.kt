/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsManagerTest {

    val settingsManager by lazy { SettingsManager.getInstance(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Test
    fun testContainsKey() {
        assertFalse(settingsManager.containsKey("notExisting"))

        // provided by DefaultsProvider
        assertTrue(settingsManager.containsKey(Settings.OVERRIDE_PROXY))
    }

}