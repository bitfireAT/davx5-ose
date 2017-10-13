/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.App
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import org.junit.Test

class DefaultsProviderTest {

    private val provider: Provider = DefaultsProvider()

    @Test
    fun testHas() {
        assertEquals(Pair(false, true), provider.has("notExisting"))
        assertEquals(Pair(true, true), provider.has(App.OVERRIDE_PROXY))
    }

    @Test
    fun testGet() {
        assertEquals(Pair("localhost", true), provider.getString(App.OVERRIDE_PROXY_HOST))
        assertEquals(Pair(8118, true), provider.getInt(App.OVERRIDE_PROXY_PORT))
    }

    @Test
    fun testPutRemove() {
        assertEquals(Pair(false, true), provider.isWritable(App.OVERRIDE_PROXY))
        assertFalse(provider.putBoolean(App.OVERRIDE_PROXY, true))
        assertFalse(provider.remove(App.OVERRIDE_PROXY))
    }

}