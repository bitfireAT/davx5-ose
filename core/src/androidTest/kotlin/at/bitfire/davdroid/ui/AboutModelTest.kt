/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.ui.about.AboutModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class AboutModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var logger: Logger

    // Model instance created once and reused across tests
    private lateinit var model: AboutModel

    @Before
    fun setup() {
        hiltRule.inject()

        // Create the model using injected dependencies
        model = AboutModel(context, ioDispatcher, logger)
    }

    @Test
    fun test_loadTransifexTranslators() = runTest {
        // Check that the function doesn't crash
        val translators = model.loadTransifexTranslators()

        // And that it's not empty
        assertTrue(translators.isNotEmpty())
    }

    @Test
    fun test_loadWeblateTranslators() = runTest {
        // Check that the function doesn't crash
        val translators = model.loadWeblateTranslators()

        // And that it's not empty
        assertTrue(translators.isNotEmpty())
    }

}