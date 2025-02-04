/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.content.ContentResolver
import at.bitfire.davdroid.network.HttpClient
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.Request
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class OkhttpClientTest {

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun inject() {
        ContentResolver.setMasterSyncAutomatically(false)
        hiltRule.inject()
    }


    @Test
    fun testIcloudWithSettings() {
        httpClientBuilder.build().use { client ->
            client.okHttpClient
                .newCall(Request.Builder()
                .get()
                .url("https://icloud.com")
                .build())
                .execute()
        }
    }

}