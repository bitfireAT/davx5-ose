/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.StandardAccountsDrawerHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

interface StandardModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface ForActivities {

        @Binds
        fun accountsDrawerHandler(handler: StandardAccountsDrawerHandler): AccountsDrawerHandler

    }

}