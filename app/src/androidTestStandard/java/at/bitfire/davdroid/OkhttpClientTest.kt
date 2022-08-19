/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.Request
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class OkhttpClientTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }


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