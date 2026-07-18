/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.startup.CrashHandlerSetup
import at.bitfire.davdroid.startup.StartupAction
import at.bitfire.davdroid.startup.TasksAppWatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface StartupPluginsModule {

    @Binds
    @IntoSet
    fun crashHandlerSetup(impl: CrashHandlerSetup): StartupAction

    @Binds
    @IntoSet
    fun tasksAppWatcher(impl: TasksAppWatcher): StartupAction

}
