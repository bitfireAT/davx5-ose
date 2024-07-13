/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.logging.Logger

@Module
@InstallIn(SingletonComponent::class)
class LoggerModule {

    @Provides
    fun globalLogger(): Logger = Logger.getGlobal()

}