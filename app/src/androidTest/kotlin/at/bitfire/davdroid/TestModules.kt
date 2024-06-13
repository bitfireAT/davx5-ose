package at.bitfire.davdroid

import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.syncadapter.PushRegistrationWorker
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dagger.hilt.testing.TestInstallIn

interface TestModules {

    @Module
    @TestInstallIn(
        components = [SingletonComponent::class],
        replaces = [PushRegistrationWorker.PushRegistrationWorkerModule::class]
    )
    abstract class FakePushRegistrationWorkerModule {
        // Provides empty set of listeners
        @Multibinds
        abstract fun defaultOnChangeListeners(): Set<DavCollectionRepository.OnChangeListener>
    }
}