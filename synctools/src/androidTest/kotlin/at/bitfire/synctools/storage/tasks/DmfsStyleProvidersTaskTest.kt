/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.os.Build
import androidx.annotation.CallSuper
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.synctools.storage.TaskProvider
import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.logging.Logger

@RunWith(Parameterized::class)

abstract class DmfsStyleProvidersTaskTest(
    val providerName: TaskProvider.ProviderName
) {

    companion object {
        @Parameterized.Parameters(name="{0}")
        @JvmStatic
        fun taskProviders() = buildList {
            add(TaskProvider.ProviderName.OpenTasks)

            // tasks.org requires Android 8
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                add(TaskProvider.ProviderName.TasksOrg)
        }
    }

    @get:Rule
    val permissionRule = GrantPermissionOrSkipRule(providerName.permissions.toSet())

    var providerOrNull: TaskProvider? = null
    lateinit var provider: TaskProvider

    @Before
    @CallSuper
    open fun prepare() {
        providerOrNull = TaskProvider.acquire(InstrumentationRegistry.getInstrumentation().context, providerName)
        assertNotNull("$providerName is not installed", providerOrNull != null)

        provider = providerOrNull!!
        Logger.getLogger(javaClass.name).fine("Using task provider: $provider")
    }

    @After
    @CallSuper
    open fun shutdown() {
        providerOrNull?.close()
    }

}