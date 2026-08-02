/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.util.Xml
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.XmlUtils
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLibrariesTest {

    /**
     * Regression test for https://github.com/bitfireAT/dav4jvm/issues/22
     */
    @Test
    fun test_Dav4jvm_HttpUtils_parseDate_IMF_FixDate_GMT() {
        assertNotNull(HttpUtils.parseDate("Mon, 04 May 2026 22:51:02 GMT"))
    }

    @Test
    fun test_Dav4jvm_XmlUtils_NewPullParser_RelaxedParsing() {
        val parser = XmlUtils.newPullParser()
        assertTrue(parser.getFeature(Xml.FEATURE_RELAXED))
    }

    @Test
    fun testOkhttpHttpUrl_PublicSuffixList() {
        // HttpUrl.topPrivateDomain() requires okhttp's internal PublicSuffixList.
        // In Android, loading the PublicSuffixList is done over AndroidX startup.
        // This test verifies that everything is working.
        assertEquals("example.com", "http://example.com".toHttpUrl().topPrivateDomain())
    }

}