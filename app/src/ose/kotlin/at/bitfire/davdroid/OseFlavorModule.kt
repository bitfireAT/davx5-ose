/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import at.bitfire.davdroid.ui.AboutActivity
import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.OpenSourceLicenseInfoProvider
import at.bitfire.davdroid.ui.OseAccountsDrawerHandler
import at.bitfire.davdroid.ui.intro.IntroFragmentFactory
import at.bitfire.davdroid.ui.intro.OpenSourceFragment
import at.bitfire.davdroid.ui.intro.PermissionsIntroFragment
import at.bitfire.davdroid.ui.intro.TasksIntroFragment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoSet

interface OseFlavorModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface AccountsDrawerHandlerModule {
        @Binds
        abstract fun accountsDrawerHandler(handler: OseAccountsDrawerHandler): AccountsDrawerHandler
    }

    @Module
    @InstallIn(ActivityComponent::class)
    interface OpenSourceLicenseInfoProviderModule {
        @Binds
        fun appLicenseInfoProviderModule(impl: OpenSourceLicenseInfoProvider): AboutActivity.AppLicenseInfoProvider
    }


    //// intro fragments ////

    // WelcomeFragment and BatteryOptimizationsFragment modules are hardcoded there

    @Module
    @InstallIn(ActivityComponent::class)
    interface OpenSourceFragmentModule {
        @Binds @IntoSet
        fun getFactory(factory: OpenSourceFragment.Factory): IntroFragmentFactory
    }

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