/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.di

import at.bitfire.davdroid.webdav.CookieStoreManager
import at.bitfire.davdroid.webdav.CredentialsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object WebdavComponentBridgeModule {

    @Provides
    fun cookieStoreManager(webdavComponentManager: WebdavComponentManager): CookieStoreManager {
        val entryPoint = EntryPoints.get(webdavComponentManager.getComponent(), WebDavEntryPoint::class.java)
        return entryPoint.cookieStoreManager()
    }

    @Provides
    fun credentialsStore(webdavComponentManager: WebdavComponentManager): CredentialsStore {
        val entryPoint = EntryPoints.get(webdavComponentManager.getComponent(), WebDavEntryPoint::class.java)
        return entryPoint.credentialsStore()
    }

}