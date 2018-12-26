/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DefaultsSettingsProviderTest {

    private val provider: SettingsProvider = DefaultsProvider()

    @Test
    fun testHas() {
        assertEquals(Pair(false, true), provider.has("notExisting"))
        assertEquals(Pair(true, true), provider.has(Settings.OVERRIDE_PROXY))
    }

    @Test
    fun testGet() {
        assertEquals(Pair("localhost", true), provider.getString(Settings.OVERRIDE_PROXY_HOST))
        assertEquals(Pair(8118, true), provider.getInt(Settings.OVERRIDE_PROXY_PORT))
    }

    @Test
    fun testPutRemove() {
        assertEquals(Pair(false, true), provider.isWritable(Settings.OVERRIDE_PROXY))
        assertFalse(provider.putBoolean(Settings.OVERRIDE_PROXY, true))
        assertFalse(provider.remove(Settings.OVERRIDE_PROXY))
    }

}