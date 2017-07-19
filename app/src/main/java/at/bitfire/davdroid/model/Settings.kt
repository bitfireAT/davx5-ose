/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase

class Settings(
        val db: SQLiteDatabase
) {

    fun getBoolean(name: String, defaultValue: Boolean): Boolean {
        db.query(ServiceDB.Settings._TABLE, arrayOf(ServiceDB.Settings.VALUE),
                "${ServiceDB.Settings.NAME}=?", arrayOf(name), null, null, null)?.use { cursor ->
            if (cursor.moveToNext() && !cursor.isNull(0))
                return cursor.getInt(0) != 0
        }
        return defaultValue
    }

    fun putBoolean(name: String, value: Boolean) {
        val values = ContentValues(2)
        values.put(ServiceDB.Settings.NAME, name)
        values.put(ServiceDB.Settings.VALUE, if (value) 1 else 0)
        db.insertWithOnConflict(ServiceDB.Settings._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }


    fun getInt(name: String, defaultValue: Int): Int {
        db.query(ServiceDB.Settings._TABLE, arrayOf(ServiceDB.Settings.VALUE),
                "${ServiceDB.Settings.NAME}=?", arrayOf(name), null, null, null)?.use { cursor ->
            if (cursor.moveToNext() && !cursor.isNull(0))
                return if (cursor.isNull(0)) defaultValue else cursor.getInt(0)
        }
        return defaultValue
    }

    fun putInt(name: String, value: Int?) {
        val values = ContentValues(2)
        values.put(ServiceDB.Settings.NAME, name)
        values.put(ServiceDB.Settings.VALUE, value)
        db.insertWithOnConflict(ServiceDB.Settings._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }


    fun getString(name: String, defaultValue: String?): String? {
        db.query(ServiceDB.Settings._TABLE, arrayOf(ServiceDB.Settings.VALUE),
                "${ServiceDB.Settings.NAME}=?", arrayOf(name), null, null, null)?.use { cursor ->
            if (cursor.moveToNext())
                return cursor.getString(0)
        }
        return defaultValue
    }

    public fun putString(name: String, value: String?) {
        val values = ContentValues(2)
        values.put(ServiceDB.Settings.NAME, name)
        values.put(ServiceDB.Settings.VALUE, value)
        db.insertWithOnConflict(ServiceDB.Settings._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }


    fun remove(name: String) {
        db.delete(ServiceDB.Settings._TABLE, "${ServiceDB.Settings.NAME}=?", arrayOf(name))
    }

}
