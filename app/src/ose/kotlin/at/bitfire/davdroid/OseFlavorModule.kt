/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import at.bitfire.davdroid.ui.AboutActivity
import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.OpenSourceLicenseInfoProvider
import at.bitfire.davdroid.ui.OseAccountsDrawerHandler
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPage
import at.bitfire.davdroid.ui.intro.IntroPage
import at.bitfire.davdroid.ui.intro.IntroPageFactory
import at.bitfire.davdroid.ui.setup.LoginTypesProvider
import at.bitfire.davdroid.ui.setup.StandardLoginTypesProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

interface OseFlavorModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface ForActivities {
        @Binds
        fun accountsDrawerHandler(impl: OseAccountsDrawerHandler): AccountsDrawerHandler

        @Binds
        fun appLicenseInfoProvider(impl: OpenSourceLicenseInfoProvider): AboutActivity.AppLicenseInfoProvider

        @Binds
        fun loginTypesProvider(impl: StandardLoginTypesProvider): LoginTypesProvider
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface Global {
        @Binds
        fun introPageFactory(impl: OseIntroPageFactory): IntroPageFactory
    }


    //// intro pages ////

    @Module
    @InstallIn(SingletonComponent::class)
    interface IntroPagesModule {
        @Provides
        @IntoSet
        fun introPage(): IntroPage = BatteryOptimizationsPage()
    }

}