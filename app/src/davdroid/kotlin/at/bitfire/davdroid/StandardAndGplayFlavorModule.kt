/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import at.bitfire.davdroid.ui.intro.IntroFragmentFactory
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
interface StandardAndGplayFlavorModule {

    //// intro fragments ////

    // WelcomeFragment and BatteryOptimizationsFragment modules are hardcoded there

    @Module
    @InstallIn(ActivityComponent::class)
    interface PermissionsIntroFragmentModule {
        @Binds @IntoSet
        fun getFactory(factory: PermissionsIntroFragment.Factory): IntroFragmentFactory
    }

    @Module
    @InstallIn(ActivityComponent::class)
    interface TasksIntroFragmentModule {
        @Binds @IntoSet
        fun getFactory(factory: TasksIntroFragment.Factory): IntroFragmentFactory
    }

}