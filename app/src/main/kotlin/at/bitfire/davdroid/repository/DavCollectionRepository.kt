/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.db.AppDatabase
import javax.inject.Inject

class DavCollectionRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.collectionDao()

    suspend fun anyWebcal(serviceId: Long) =
        dao.anyWebcal(serviceId)

    suspend fun setCollectionSync(id: Long, forceReadOnly: Boolean) {
        dao.updateSync(id, forceReadOnly)
    }

}