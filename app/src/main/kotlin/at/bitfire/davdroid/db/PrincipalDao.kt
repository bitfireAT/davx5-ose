/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import okhttp3.HttpUrl

@Dao
interface PrincipalDao {

    @Query("SELECT * FROM principal WHERE id=:id")
    fun get(id: Long): Principal

    @Query("SELECT * FROM principal WHERE id=:id")
    fun getLive(id: Long): LiveData<Principal?>

    @Query("SELECT * FROM principal WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<Principal>

    @Query("SELECT * FROM principal WHERE serviceId=:serviceId AND url=:url")
    fun getByUrl(serviceId: Long, url: HttpUrl): Principal?

    /**
     * Gets all principals who do not own any collections
     */
    @Query("SELECT * FROM principal WHERE principal.id NOT IN (SELECT ownerId FROM collection WHERE ownerId IS NOT NULL)")
    fun getAllWithoutCollections(): List<Principal>

    @Insert
    fun insert(principal: Principal): Long

    @Update
    fun update(principal: Principal)

    @Delete
    fun delete(principal: Principal)

    /**
     * Inserts, updates or just gets existing principal if its display name has not
     * changed (will not update/overwrite with null values).
     *
     * @param principal Principal to be inserted or updated
     * @return ID of the newly inserted or already existing principal
     */
    fun insertOrUpdate(serviceId: Long, principal: Principal): Long =
        getByUrl(serviceId, principal.url)?.let { oldPrincipal ->
            if (principal.displayName != oldPrincipal.displayName)
                update(principal.copy(id = oldPrincipal.id))
            return oldPrincipal.id
        } ?: insert(principal)

}