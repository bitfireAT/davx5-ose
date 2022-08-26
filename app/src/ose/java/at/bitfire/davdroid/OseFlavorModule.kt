/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.OseAccountsDrawerHandler
import at.bitfire.davdroid.ui.intro.IntroFragmentFactory
import at.bitfire.davdroid.ui.intro.OpenSourceFragment
import at.bitfire.davdroid.ui.intro.PermissionsIntroFragment
import at.bitfire.davdroid.ui.intro.TasksIntroFragment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class OseFlavorModule {

    //// navigation drawer handler ////

    @Binds
    abstract fun accountsDrawerHandler(handler: OseAccountsDrawerHandler): AccountsDrawerHandler


    //// intro fragments ////

    // WelcomeFragment and BatteryOptimizationsFragment modules are hardcoded there

    @Module
    @InstallIn(ActivityComponent::class)
    abstract class OpenSourceFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: OpenSourceFragment.Factory): IntroFragmentFactory
    }

    @Module
    @InstallIn(ActivityComponent::class)
    abstract class PermissionsIntroFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: PermissionsIntroFragment.Factory): IntroFragmentFactory
    }

    @Module
    @InstallIn(ActivityComponent::class)
    abstract class TasksIntroFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: TasksIntroFragment.Factory): IntroFragmentFactory
    }

}