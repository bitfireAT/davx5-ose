/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDavMountDao {

    @Delete
    suspend fun deleteAsync(mount: WebDavMount)

    @Query("SELECT * FROM webdav_mount ORDER BY name, url")
    fun getAll(): List<WebDavMount>

    @Query("SELECT * FROM webdav_mount ORDER BY name, url")
    fun getAllFlow(): Flow<List<WebDavMount>>

    @Query("SELECT * FROM webdav_mount WHERE id=:id")
    fun getById(id: Long): WebDavMount

    @Insert
    fun insert(mount: WebDavMount): Long


    // complex queries

    /**
     * Gets a list of mounts with the quotas of their root document, if available.
     */
    @Query("SELECT webdav_mount.*, quotaAvailable, quotaUsed FROM webdav_mount " +
            "LEFT JOIN webdav_document ON (webdav_mount.id=webdav_document.mountId AND webdav_document.parentId IS NULL) " +
            "ORDER BY webdav_mount.name, webdav_mount.url")
    fun getAllWithQuotaFlow(): Flow<List<WebDavMountWithQuota>>

}