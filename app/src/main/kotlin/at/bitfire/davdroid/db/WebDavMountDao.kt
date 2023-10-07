/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WebDavMountDao {

    @Delete
    fun delete(mount: WebDavMount)

    @Query("SELECT * FROM webdav_mount ORDER BY name, url")
    fun getAll(): List<WebDavMount>

    @Query("SELECT * FROM webdav_mount ORDER BY name, url")
    fun getAllLive(): LiveData<List<WebDavMount>>

    @Query("SELECT * FROM webdav_mount WHERE id=:id")
    fun getById(id: Long): WebDavMount

    @Insert
    fun insert(mount: WebDavMount): Long

}