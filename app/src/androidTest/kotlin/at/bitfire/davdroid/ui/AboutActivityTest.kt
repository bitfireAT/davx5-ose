/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.logging.Logger

class AboutActivityTest {
    @Test
    fun test_loadTranslations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = Logger.getLogger("AboutActivityTest")
        val model = AboutActivity.Model(context, Dispatchers.IO, logger)

        // Check that the function doesn't crash
        val translations = model.loadTranslations()

        // And that it's not empty
        assertTrue("Expected translations to be non-empty", translations.isNotEmpty())
    }
}
