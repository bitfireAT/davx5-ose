/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.log.LogcatHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Singleton

@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LoggerModule::class]
)
@Module
class TestLoggerModule {

    @Provides
    @Singleton
    fun logger(): Logger = Logger.getGlobal().apply {
        level = Level.ALL
        addHandler(LogcatHandler())
    }

}