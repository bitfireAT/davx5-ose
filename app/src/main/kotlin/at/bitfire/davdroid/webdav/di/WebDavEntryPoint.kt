/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.di

import at.bitfire.davdroid.di.Bridged
import at.bitfire.davdroid.webdav.CookieStoreManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn

@EntryPoint
@InstallIn(WebdavComponent::class)
internal interface WebDavEntryPoint {

    @Bridged    // without this qualifier, the CookieStoreManager from CookieStoreManagerBridgedModule would be used
    fun cookieStoreManager(): CookieStoreManager

}