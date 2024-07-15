package at.bitfire.davdroid

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.push.PushRegistrationWorker
import at.bitfire.davdroid.repository.DavCollectionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.Multibinds

interface TestModules {

    @Module
    @TestInstallIn(
        components = [SingletonComponent::class],
        replaces = [PushRegistrationWorker.PushRegistrationWorkerModule::class]
    )
    abstract class TestPushRegistrationWorkerModule {
        // provides empty set of listeners
        @Multibinds
        abstract fun defaultOnChangeListeners(): Set<DavCollectionRepository.OnChangeListener>
    }

}