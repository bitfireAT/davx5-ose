/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsManagerTest: KoinComponent {

    val settingsManager by inject<SettingsManager>()

    @Test
    fun testContainsKey_NotExisting() {
        assertFalse(settingsManager.containsKey("notExisting"))
    }

    @Test
    fun testContainsKey_Existing() {
        // provided by DefaultsProvider
        assertEquals(Settings.PROXY_TYPE_SYSTEM, settingsManager.getInt(Settings.PROXY_TYPE))
    }

}