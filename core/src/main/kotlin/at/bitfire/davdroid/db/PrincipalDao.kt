/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.ktor.http.Url

@Dao
interface PrincipalDao {

    @Query("SELECT * FROM principal WHERE id=:id")
    suspend fun get(id: Long): Principal

    @Query("SELECT * FROM principal WHERE id=:id")
    fun getBlocking(id: Long): Principal

    @Query("SELECT * FROM principal WHERE serviceId=:serviceId")
    suspend fun getByService(serviceId: Long): List<Principal>

    @Query("SELECT * FROM principal WHERE serviceId=:serviceId AND url=:url")
    suspend fun getByUrl(serviceId: Long, url: Url): Principal?

    /**
     * Gets all principals who do not own any collections
     */
    @Query("SELECT * FROM principal WHERE principal.id NOT IN (SELECT ownerId FROM collection WHERE ownerId IS NOT NULL)")
    suspend fun getAllWithoutCollections(): List<Principal>

    @Insert
    suspend fun insert(principal: Principal): Long

    @Update
    suspend fun update(principal: Principal)

    @Delete
    suspend fun delete(principal: Principal)

    /**
     * Inserts, updates or just gets existing principal if its display name has not
     * changed (will not update/overwrite with null values).
     *
     * @param principal Principal to be inserted or updated
     * @return ID of the newly inserted or already existing principal
     */
    suspend fun insertOrUpdate(serviceId: Long, principal: Principal): Long {
        // Try to get existing principal by URL
        val oldPrincipal = getByUrl(serviceId, principal.url)

        // Insert new principal if not existing
        if (oldPrincipal == null)
            return insert(principal)

        // Otherwise update the existing principal
        if (principal.displayName != oldPrincipal.displayName)
            update(principal.copy(id = oldPrincipal.id))

        // In any case return the id of the principal
        return oldPrincipal.id
    }

}
