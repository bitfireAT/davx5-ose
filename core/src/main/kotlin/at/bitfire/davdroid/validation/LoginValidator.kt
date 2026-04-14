/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.validation

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl


/**
 * Used to validate login attempts.
 */
interface LoginValidator {

    /**
     * Called before login/setup to validate if the server is allowed for this app variant.
     * Must be thread-safe.
     *
     * @param baseUrl The base URL of the server to validate
     * @return whether login/setup shall proceed (false to abort)
     */
    fun beforeLogin(baseUrl: HttpUrl): Boolean

}

@Module
@InstallIn(SingletonComponent::class)
interface LoginValidatorModule {
    @BindsOptionalOf
    fun loginValidator(): LoginValidator
}
