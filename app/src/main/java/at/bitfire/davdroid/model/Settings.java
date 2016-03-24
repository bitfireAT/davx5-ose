/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import lombok.Cleanup;

public class Settings {

    final SQLiteDatabase db;

    public Settings(SQLiteDatabase db) {
        this.db = db;
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        @Cleanup Cursor cursor = db.query(ServiceDB.Settings._TABLE, new String[] { ServiceDB.Settings.VALUE },
                ServiceDB.Settings.NAME + "=?", new String[] { name }, null, null, null);
        if (cursor.moveToNext())
            return cursor.getInt(0) != 0;
        else
            return defaultValue;
    }

    public void putBoolean(String name, boolean value) {
        ContentValues values = new ContentValues(2);
        values.put(ServiceDB.Settings.NAME, name);
        values.put(ServiceDB.Settings.VALUE, value ? 1 : 0);
        db.insertWithOnConflict(ServiceDB.Settings._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void remove(String name) {
        db.delete(ServiceDB.Settings._TABLE, ServiceDB.Settings.NAME + "=?", new String[] { name });
    }

}
