/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface GplayFlavorModule {

    @Binds
    fun accountsDrawerHandler(impl: GplayAccountsDrawerHandler): AccountsDrawerHandler

    @Binds
    fun appLicenseInfoProvider(impl: GplayLicenseInfoProvider): AboutActivity.AppLicenseInfoProvider

}