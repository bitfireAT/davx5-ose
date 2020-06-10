/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultsSettingsProviderTest {

    private val provider: SettingsProvider = DefaultsProvider()

    @Test
    fun testContains() {
        assertEquals(false, provider.contains("notExisting"))
        assertEquals(true, provider.contains(Settings.OVERRIDE_PROXY))
    }

    @Test
    fun testGet() {
        assertEquals("localhost", provider.getString(Settings.OVERRIDE_PROXY_HOST))
        assertEquals(8118, provider.getInt(Settings.OVERRIDE_PROXY_PORT))
    }


}