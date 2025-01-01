/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.OpenSourceLicenseInfoProvider
import at.bitfire.davdroid.ui.OseAccountsDrawerHandler
import at.bitfire.davdroid.ui.about.AppLicenseInfoProvider
import at.bitfire.davdroid.ui.intro.IntroPageFactory
import at.bitfire.davdroid.ui.setup.LoginTypesProvider
import at.bitfire.davdroid.ui.setup.StandardLoginTypesProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent

interface OseFlavorModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface ForActivities {
        @Binds
        fun loginTypesProvider(impl: StandardLoginTypesProvider): LoginTypesProvider
    }

    @Module
    @InstallIn(ViewModelComponent::class)
    interface ForViewModels {
        @Binds
        fun accountsDrawerHandler(impl: OseAccountsDrawerHandler): AccountsDrawerHandler

        @Binds
        fun appLicenseInfoProvider(impl: OpenSourceLicenseInfoProvider): AppLicenseInfoProvider

        @Binds
        fun loginTypesProvider(impl: StandardLoginTypesProvider): LoginTypesProvider
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface Global {
        @Binds
        fun introPageFactory(impl: OseIntroPageFactory): IntroPageFactory
    }

}