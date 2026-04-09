/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.di

import at.bitfire.davdroid.ui.actioncards.ActionCardProvider
import com.davx5.ose.actioncards.OseActionCardProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ActionCardProviderModule {

    /**
     * Bind the OSE implementation of ActionCardProvider (OseActionCardProvider) to override the default empty implementation.
     */
    @Binds
    abstract fun bindActionCardProvider(impl: OseActionCardProvider): ActionCardProvider
}