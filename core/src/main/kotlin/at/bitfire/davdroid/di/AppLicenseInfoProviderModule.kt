/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.ui.about.AboutActivity.AppLicenseInfoProvider
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface AppLicenseInfoProviderModule {

    // allows to inject Optional<AppLicenseInfoProvider> by providing an empty Optional as default
    @BindsOptionalOf
    fun appLicenseInfoProvider(): AppLicenseInfoProvider

}
