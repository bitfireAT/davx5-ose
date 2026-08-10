/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import android.accounts.AccountManager
import android.content.Context
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
interface AndroidServicesModule {

    @Binds
    fun accountManager(@ApplicationContext context: Context): AccountManager =
        AccountManager.get(context)

}