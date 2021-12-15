/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Request
import org.junit.Test

class OkhttpClientTest {

    @Test
    fun testIcloudWithSettings() {
        val client = HttpClient.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
                .build()
        client.okHttpClient.newCall(Request.Builder()
                .get()
                .url("https://icloud.com")
                .build())
                .execute()
    }

}