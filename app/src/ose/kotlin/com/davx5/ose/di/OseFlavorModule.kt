/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.di

import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.OseAccountsDrawerHandler
import at.bitfire.davdroid.ui.about.AboutActivity
import at.bitfire.davdroid.ui.intro.IntroPageFactory
import at.bitfire.davdroid.ui.setup.LoginTypesProvider
import com.davx5.ose.ui.about.OpenSourceLicenseInfoProvider
import com.davx5.ose.ui.intro.OseIntroPageFactory
import com.davx5.ose.ui.setup.StandardLoginTypesProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent

interface OseModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface ForActivities {
        @Binds
        fun accountsDrawerHandler(impl: OseAccountsDrawerHandler): AccountsDrawerHandler

        @Binds
        fun loginTypesProvider(impl: StandardLoginTypesProvider): LoginTypesProvider
    }

    @Module
    @InstallIn(ViewModelComponent::class)
    interface ForViewModels {
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

}