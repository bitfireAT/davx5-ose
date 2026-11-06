/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.accounts.AndroidAccountRemover
import at.bitfire.davdroid.repository.OnBeforeDeleteListener
import at.bitfire.davdroid.resource.local.AddressBookAccountRemover
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface OnBeforeDeleteListenersModule {

    @Binds
    @IntoSet
    fun addressBookAccountRemover(impl: AddressBookAccountRemover): OnBeforeDeleteListener

    @Binds
    @IntoSet
    fun androidAccountRemover(impl: AndroidAccountRemover): OnBeforeDeleteListener

}
