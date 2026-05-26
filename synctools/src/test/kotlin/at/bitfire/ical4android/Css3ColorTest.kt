/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.synctools.icalendar.Css3Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Css3ColorTest {

    @Test
    fun testColorFromString() {
        // color name
        assertEquals(0xffffff00.toInt(), Css3Color.colorFromString("yellow"))

        // RGB value
        assertEquals(0xffffff00.toInt(), Css3Color.colorFromString("#ffff00"))

        // ARGB value
        assertEquals(0xffffff00.toInt(), Css3Color.colorFromString("#ffffff00"))

        // empty value
        assertNull(Css3Color.colorFromString(""))

        // invalid value
        assertNull(Css3Color.colorFromString("DoesNotExist"))
    }

    @Test
    fun testFromString() {
        // lower case
        assertEquals(0xffffff00.toInt(), Css3Color.fromString("yellow")?.argb)

        // capitalized
        assertEquals(0xffffff00.toInt(), Css3Color.fromString("Yellow")?.argb)

        // not-existing color
        assertNull(Css3Color.fromString("DoesNotExist"))
    }

    @Test
    fun testNearestMatch() {
        // every color is its own nearest match
        Css3Color.entries.forEach {
            assertEquals(it.argb, Css3Color.nearestMatch(it.argb).argb)
        }
    }

}