// SPDX-FileCopyrightText: 2023 DAVx⁵ contributors <https://github.com/bitfireAT/davx5-ose/graphs/contributors>
//
// SPDX-License-Identifier: GPL-3.0-only

package at.bitfire.davdroid.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsActivityTest {
    @Test
    fun testResourceQualifierToLanguageTag() {
        assertEquals("en", AppSettingsActivity.resourceQualifierToLanguageTag("en"))
        assertEquals("en-GB", AppSettingsActivity.resourceQualifierToLanguageTag("en-GB"))
        assertEquals("en-GB", AppSettingsActivity.resourceQualifierToLanguageTag("en-rGB"))
    }
}
