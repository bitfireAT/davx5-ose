/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.di

import at.bitfire.davdroid.di.Bridged
import at.bitfire.davdroid.webdav.CookieStoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn

@Module
@InstallIn(WebdavComponent::class)
object WebdavComponentModule {

    @Provides
    @WebdavScoped   // "singleton" per WebdavComponent
    @Bridged        // qualifier used in WebDavEntryPoint
    fun cookieStoreManager() = CookieStoreManager()

}