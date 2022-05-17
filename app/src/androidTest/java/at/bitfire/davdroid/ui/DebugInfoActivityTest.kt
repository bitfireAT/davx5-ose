/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugInfoActivityTest {

    @Test
    fun testIntentBuilder_LargeLocalResource() {
        val a = 'A'.code.toByte()
        val intent = DebugInfoActivity.IntentBuilder(InstrumentationRegistry.getInstrumentation().context)
            .withLocalResource(String(ByteArray(1024*1024, { a })))
            .build()
        val expected = StringBuilder(DebugInfoActivity.IntentBuilder.MAX_ELEMENT_SIZE)
        expected.append(String(ByteArray(DebugInfoActivity.IntentBuilder.MAX_ELEMENT_SIZE - 3, { a })))
        expected.append("...")
        assertEquals(expected.toString(), intent.getStringExtra("localResource"))
    }

    @Test
    fun testIntentBuilder_LargeLogs() {
        val a = 'A'.code.toByte()
        val intent = DebugInfoActivity.IntentBuilder(InstrumentationRegistry.getInstrumentation().context)
            .withLogs(String(ByteArray(1024*1024, { a })))
            .build()
        val expected = StringBuilder(DebugInfoActivity.IntentBuilder.MAX_ELEMENT_SIZE)
        expected.append(String(ByteArray(DebugInfoActivity.IntentBuilder.MAX_ELEMENT_SIZE - 3, { a })))
        expected.append("...")
        assertEquals(expected.toString(), intent.getStringExtra("logs"))
    }

}