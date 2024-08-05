/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Principal
import javax.inject.Inject

class PrincipalRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.principalDao()

    fun get(id: Long): Principal = dao.get(id)

}