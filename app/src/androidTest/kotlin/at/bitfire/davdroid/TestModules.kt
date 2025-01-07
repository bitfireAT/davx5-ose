package at.bitfire.davdroid

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.push.PushRegistrationWorkerManager
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.startup.StartupPlugin
import at.bitfire.davdroid.startup.TasksAppWatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.Multibinds
import javax.inject.Provider
import javax.inject.Qualifier

/*@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TargetContext

@Module
@InstallIn(SingletonComponent::class)
internal object TargetContextModule {
    @Provides @TargetContext
    fun targetContext(): Provider<Context> = object: Provider<Context> {
        override fun get() = InstrumentationRegistry.getInstrumentation().targetContext
    }
}*/

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