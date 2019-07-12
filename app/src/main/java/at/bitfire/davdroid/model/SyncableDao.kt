package at.bitfire.davdroid.model

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update

interface SyncableDao<T: IdEntity> {

    @Insert
    fun insert(items: List<T>)

    @Update
    fun update(item: T)

    @Delete
    fun delete(item: T)

}