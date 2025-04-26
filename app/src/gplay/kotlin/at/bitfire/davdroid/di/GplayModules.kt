/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.ui.AboutActivity
import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.GplayAccountsDrawerHandler
import at.bitfire.davdroid.ui.GplayLicenseInfoProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

interface GplayModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface ForActivities {

        @Binds
        fun accountsDrawerHandler(impl: GplayAccountsDrawerHandler): AccountsDrawerHandler

        @Binds
        fun appLicenseInfoProvider(impl: GplayLicenseInfoProvider): AboutActivity.AppLicenseInfoProvider

    }

}