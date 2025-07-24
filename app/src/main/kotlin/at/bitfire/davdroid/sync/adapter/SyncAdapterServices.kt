/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.adapter

import android.app.Service
import android.content.Intent
import android.os.IBinder
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

    override fun onBind(intent: Intent?): IBinder {
        // create sync adapter via Hilt
        val entryPoint = EntryPointAccessors.fromApplication<SyncAdapterServicesEntryPoint>(this)
        return entryPoint.syncAdapter().getBinder()
    }

}

// exported sync adapter services; we need a separate class for each authority
class CalendarsSyncAdapterService: SyncAdapterService()
class ContactsSyncAdapterService: SyncAdapterService()
class JtxSyncAdapterService: SyncAdapterService()
class OpenTasksSyncAdapterService: SyncAdapterService()
class TasksOrgSyncAdapterService: SyncAdapterService()