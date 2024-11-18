package at.bitfire.davdroid

import at.bitfire.davdroid.push.PushRegistrationWorkerManager
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.startup.StartupPlugin
import at.bitfire.davdroid.startup.TasksAppWatcher
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.Multibinds

interface TestModules {

    // remove PushRegistrationWorkerModule from Android tests
    @Module
    @TestInstallIn(
        components = [SingletonComponent::class],
        replaces = [PushRegistrationWorkerManager.PushRegistrationWorkerModule::class]
    )
    abstract class TestPushRegistrationWorkerModule {
        // provides empty set of listeners
        @Multibinds
        abstract fun empty(): Set<DavCollectionRepository.OnChangeListener>
    }

    // remove TasksAppWatcherModule from Android tests
    @Module
    @TestInstallIn(
        components = [SingletonComponent::class],
        replaces = [TasksAppWatcher.TasksAppWatcherModule::class]
    )
    abstract class TestTasksAppWatcherModuleModule {
        // provides empty set of plugins
        @Multibinds
        abstract fun empty(): Set<StartupPlugin>
    }

}