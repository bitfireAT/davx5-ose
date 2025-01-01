package at.bitfire.davdroid.ui.about

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
interface AppLicenseInfoProviderModule {
    @BindsOptionalOf
    fun appLicenseInfoProvider(): AppLicenseInfoProvider
}
