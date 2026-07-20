/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.startup.AppCompatThemeSetup
import at.bitfire.davdroid.startup.CrashHandlerSetup
import at.bitfire.davdroid.startup.DynamicShortcutsAction
import at.bitfire.davdroid.startup.EnableAccountsCleanupAction
import at.bitfire.davdroid.startup.StartupAction
import at.bitfire.davdroid.startup.StrictModeSetup
import at.bitfire.davdroid.startup.TasksAppWatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface StartupActionsModule {

    @Binds
    @IntoSet
    fun appCompatThemeSetup(impl: AppCompatThemeSetup): StartupAction

    @Binds
    @IntoSet
    fun crashHandlerSetup(impl: CrashHandlerSetup): StartupAction

    @Binds
    @IntoSet
    fun dynamicShortcutsAction(impl: DynamicShortcutsAction): StartupAction

    @Binds
    @IntoSet
    fun enableAccountsCleanupAction(impl: EnableAccountsCleanupAction): StartupAction

    @Binds
    @IntoSet
    fun strictModeSetup(impl: StrictModeSetup): StartupAction

    @Binds
    @IntoSet
    fun tasksAppWatcher(impl: TasksAppWatcher): StartupAction

}
