/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.content.Context
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.SyncStats
import at.bitfire.davdroid.sync.SyncDataType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import javax.inject.Inject

class DavSyncStatsRepository @Inject constructor(
    @ApplicationContext val context: Context,
    db: AppDatabase
) {

    private val dao = db.syncStatsDao()

    data class LastSynced(
        val dataType: String,
        val lastSynced: Long
    )
    fun getLastSyncedFlow(collectionId: Long): Flow<List<LastSynced>> =
        dao.getByCollectionIdFlow(collectionId).map { list ->
            val collator = Collator.getInstance()
            list.map { stats ->
                LastSynced(
                    dataType = stats.dataType,
                    lastSynced = stats.lastSync
                )
            }.sortedWith { a, b ->
                collator.compare(a.dataType, b.dataType)
            }
        }

    suspend fun logSyncTime(collectionId: Long, dataType: SyncDataType, lastSync: Long = System.currentTimeMillis()) {
        dao.insertOrReplace(SyncStats(
            id = 0,
            collectionId = collectionId,
            dataType = dataType.name,
            lastSync = lastSync
        ))
    }

}