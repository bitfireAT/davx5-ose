/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.jetbrains.annotations.Async.Execute

@Dao
interface CollectionDao {

    @Query("SELECT DISTINCT color FROM collection WHERE serviceId=:id")
    fun colorsByServiceLive(id: Long): LiveData<List<Int>>

    @Query("SELECT * FROM collection WHERE id=:id")
    fun get(id: Long): Collection?

    @Query("SELECT * FROM collection WHERE id=:id")
    fun getLive(id: Long): LiveData<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND homeSetId IS :homeSetId")
    fun getByServiceAndHomeset(serviceId: Long, homeSetId: Long?): List<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND type=:type ORDER BY displayName COLLATE NOCASE, url COLLATE NOCASE")
    fun getByServiceAndType(serviceId: Long, type: String): List<Collection>

    /**
     * Returns collections which
     *   - support VEVENT and/or VTODO (= supported calendar collections), or
     *   - have supportsVEVENT = supportsVTODO = null (= address books)
     */
    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND type=:type " +
            "AND (supportsVTODO OR supportsVEVENT OR supportsVJOURNAL OR (supportsVEVENT IS NULL AND supportsVTODO IS NULL AND supportsVJOURNAL IS NULL)) ORDER BY displayName COLLATE NOCASE, URL COLLATE NOCASE")
    fun pageByServiceAndType(serviceId: Long, type: String): PagingSource<Int, Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND sync")
    fun getByServiceAndSync(serviceId: Long): List<Collection>

    @Query("SELECT collection.* FROM collection, homeset WHERE collection.serviceId=:serviceId AND type=:type AND homeSetId=homeset.id AND homeset.personal ORDER BY collection.displayName COLLATE NOCASE, collection.url COLLATE NOCASE")
    fun pagePersonalByServiceAndType(serviceId: Long, type: String): PagingSource<Int, Collection>

    @Deprecated("Use getByServiceAndUrl instead")
    @Query("SELECT * FROM collection WHERE url=:url")
    fun getByUrl(url: String): Collection?

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND url=:url")
    fun getByServiceAndUrl(serviceId: Long, url: String): Collection?

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND type='${Collection.TYPE_CALENDAR}' AND supportsVEVENT AND sync ORDER BY displayName COLLATE NOCASE, url COLLATE NOCASE")
    fun getSyncCalendars(serviceId: Long): List<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND type='${Collection.TYPE_CALENDAR}' AND (supportsVTODO OR supportsVJOURNAL) AND sync ORDER BY displayName COLLATE NOCASE, url COLLATE NOCASE")
    fun getSyncJtxCollections(serviceId: Long): List<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND type='${Collection.TYPE_CALENDAR}' AND supportsVTODO AND sync ORDER BY displayName COLLATE NOCASE, url COLLATE NOCASE")
    fun getSyncTaskLists(serviceId: Long): List<Collection>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(collection: Collection): Long

    @Update
    fun update(collection: Collection)

    @Query("UPDATE collection SET forceReadOnly=:forceReadOnly WHERE id=:id")
    fun updateForceReadOnly(id: Long, forceReadOnly: Boolean)

    @Query("UPDATE collection SET sync=:sync WHERE id=:id")
    fun updateSync(id: Long, sync: Boolean)

    /**
     * Tries to insert new row, but updates existing row if already present.
     * This method preserves the primary key, as opposed to using "@Insert(onConflict = OnConflictStrategy.REPLACE)"
     * which will create a new row with incremented ID and thus breaks entity relationships!
     *
     * @param collection Collection to be inserted or updated
     * @return ID of the row, that has been inserted or updated. -1 If the insert fails due to other reasons.
     */
    @Transaction
    fun insertOrUpdateByUrl(collection: Collection): Long = getByServiceAndUrl(
        collection.serviceId,
        collection.url.toString()
    )?.let { localCollection ->
        update(collection.copy(id = localCollection.id))
        localCollection.id
    } ?: insert(collection)

    /**
     * Inserts or updates the collection. On update it will not update flag values ([Collection.sync],
     * [Collection.forceReadOnly]), but use the values of the already existing collection.
     *
     * @param newCollection Collection to be inserted or updated
     */
    fun insertOrUpdateByUrlAndRememberFlags(newCollection: Collection) {
        // remember locally set flags
        getByServiceAndUrl(newCollection.serviceId, newCollection.url.toString())?.let { oldCollection ->
            newCollection.sync = oldCollection.sync
            newCollection.forceReadOnly = oldCollection.forceReadOnly
        }

        // commit to database
        insertOrUpdateByUrl(newCollection)
    }

    @Delete
    fun delete(collection: Collection)

}
