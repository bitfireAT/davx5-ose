/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.util.Xml
import at.bitfire.dav4jvm.XmlUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class Dav4jvmTest {

    @Test
    fun test_Dav4jvm_XmlUtils_NewPullParser_RelaxedParsing() {
        val parser = XmlUtils.newPullParser()
        assertTrue(parser.getFeature(Xml.FEATURE_RELAXED))
    }

}