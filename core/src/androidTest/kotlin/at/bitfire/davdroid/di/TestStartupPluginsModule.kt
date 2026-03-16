/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.startup.StartupPlugin
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.Multibinds

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [StartupPluginsModule::class]
)
abstract class TestStartupPluginsModule {

    // provides empty set of startup plugins so that nothing interferes with tests
    @Multibinds
    abstract fun empty(): Set<StartupPlugin>

}