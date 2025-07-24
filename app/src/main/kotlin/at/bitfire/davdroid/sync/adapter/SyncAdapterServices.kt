/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.adapter

import android.app.Service
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

abstract class SyncAdapterService: Service() {

    /**
     * We don't use @AndroidEntryPoint / @Inject because it's unavoidable that instrumented tests sometimes accidentally / asynchronously
     * create a [SyncAdapterService] instance before Hilt is initialized by the HiltTestRunner.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncAdapterServicesEntryPoint {
        fun syncAdapter(): SyncAdapter
    }

    // create syncAdapter on demand and cache it
    val syncAdapter by lazy {
        val entryPoint = EntryPointAccessors.fromApplication<SyncAdapterServicesEntryPoint>(this)
        entryPoint.syncAdapter()
    }

    override fun onBind(intent: Intent?) = syncAdapter.getBinder()

}

// exported sync adapter services; we need a separate class for each authority
class CalendarsSyncAdapterService: SyncAdapterService()
class ContactsSyncAdapterService: SyncAdapterService()
class JtxSyncAdapterService: SyncAdapterService()
class OpenTasksSyncAdapterService: SyncAdapterService()
class TasksOrgSyncAdapterService: SyncAdapterService()