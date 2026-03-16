/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.di

import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.OseAccountsDrawerHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface AccountsDrawerHandlerModule {
    @Binds
    fun accountsDrawerHandler(impl: OseAccountsDrawerHandler): AccountsDrawerHandler
}
