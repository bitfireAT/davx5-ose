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
