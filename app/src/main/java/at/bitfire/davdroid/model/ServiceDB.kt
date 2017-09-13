/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import at.bitfire.davdroid.log.Logger
import java.io.Closeable

class ServiceDB {

    object Settings {
        @JvmField val _TABLE = "settings"
        @JvmField val NAME = "setting"
        @JvmField val VALUE = "value"
    }

    object Services {
        @JvmField val _TABLE = "services"
        @JvmField val ID = "_id"
        @JvmField val ACCOUNT_NAME = "accountName"
        @JvmField val SERVICE = "service"
        @JvmField val PRINCIPAL = "principal"

        // allowed values for SERVICE column
        @JvmField val SERVICE_CALDAV = "caldav"
        @JvmField val SERVICE_CARDDAV = "carddav"
    }

    object HomeSets {
        @JvmField val _TABLE = "homesets"
        @JvmField val ID = "_id"
        @JvmField val SERVICE_ID = "serviceID"
        @JvmField val URL = "url"
    }

    object Collections {
        @JvmField val _TABLE = "collections"
        @JvmField val ID = "_id"
        @JvmField val TYPE = "type"
        @JvmField val SERVICE_ID = "serviceID"
        @JvmField val URL = "url"
        @JvmField val READ_ONLY = "readOnly"
        @JvmField val DISPLAY_NAME = "displayName"
        @JvmField val DESCRIPTION = "description"
        @JvmField val COLOR = "color"
        @JvmField val TIME_ZONE = "timezone"
        @JvmField val SUPPORTS_VEVENT = "supportsVEVENT"
        @JvmField val SUPPORTS_VTODO = "supportsVTODO"
        @JvmField val SOURCE = "source"
        @JvmField val SYNC = "sync"
    }

    companion object {

        @JvmStatic
        fun onRenameAccount(db: SQLiteDatabase, oldName: String, newName: String) {
            val values = ContentValues(1)
            values.put(Services.ACCOUNT_NAME, newName)
            db.updateWithOnConflict(Services._TABLE, values, Services.ACCOUNT_NAME + "=?", arrayOf(oldName), SQLiteDatabase.CONFLICT_REPLACE)
        }

    }


    class OpenHelper(
            context: Context
    ): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), Closeable {

        companion object {
            val DATABASE_NAME = "services.db"
            val DATABASE_VERSION = 2
        }

        override fun onConfigure(db: SQLiteDatabase) {
            setWriteAheadLoggingEnabled(true)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            Logger.log.info("Creating database " + db.path)

            db.execSQL("CREATE TABLE ${Settings._TABLE}(" +
                    "${Settings.NAME} TEXT NOT NULL," +
                    "${Settings.VALUE} TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX settings_name ON ${Settings._TABLE} (${Settings.NAME})")

            db.execSQL("CREATE TABLE ${Services._TABLE}(" +
                    "${Services.ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "${Services.ACCOUNT_NAME} TEXT NOT NULL," +
                    "${Services.SERVICE} TEXT NOT NULL," +
                    "${Services.PRINCIPAL} TEXT NULL)")
            db.execSQL("CREATE UNIQUE INDEX services_account ON ${Services._TABLE} (${Services.ACCOUNT_NAME},${Services.SERVICE})")

            db.execSQL("CREATE TABLE ${HomeSets._TABLE}(" +
                    "${HomeSets.ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "${HomeSets.SERVICE_ID} INTEGER NOT NULL REFERENCES ${Services._TABLE} ON DELETE CASCADE," +
                    "${HomeSets.URL} TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX homesets_service_url ON ${HomeSets._TABLE}(${HomeSets.SERVICE_ID},${HomeSets.URL})")

            db.execSQL("CREATE TABLE ${Collections._TABLE}(" +
                    "${Collections.ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "${Collections.SERVICE_ID} INTEGER NOT NULL REFERENCES ${Services._TABLE} ON DELETE CASCADE," +
                    "${Collections.TYPE} TEXT NOT NULL," +
                    "${Collections.URL} TEXT NOT NULL," +
                    "${Collections.READ_ONLY} INTEGER DEFAULT 0 NOT NULL," +
                    "${Collections.DISPLAY_NAME} TEXT NULL," +
                    "${Collections.DESCRIPTION} TEXT NULL," +
                    "${Collections.COLOR} INTEGER NULL," +
                    "${Collections.TIME_ZONE} TEXT NULL," +
                    "${Collections.SUPPORTS_VEVENT} INTEGER NULL," +
                    "${Collections.SUPPORTS_VTODO} INTEGER NULL," +
                    "${Collections.SOURCE} TEXT NULL," +
                    "${Collections.SYNC} INTEGER DEFAULT 0 NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX collections_service_url ON ${Collections._TABLE}(${Collections.SERVICE_ID},${Collections.URL})")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion == 1 && newVersion == 2) {
                // the only possible migration at the moment
                db.execSQL("ALTER TABLE ${Collections._TABLE} ADD COLUMN ${Collections.TYPE} TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ${Collections._TABLE} ADD COLUMN ${Collections.SOURCE} TEXT NULL")
                db.execSQL("UPDATE ${Collections._TABLE} SET ${Collections.TYPE}=(" +
                            "SELECT CASE ${Services.SERVICE} WHEN ? THEN ? ELSE ? END " +
                                "FROM ${Services._TABLE} WHERE ${Services.ID}=${Collections._TABLE}.${Collections.SERVICE_ID}" +
                        ")",
                        arrayOf(Services.SERVICE_CALDAV, CollectionInfo.Type.CALENDAR, CollectionInfo.Type.ADDRESS_BOOK))
            }
        }


        fun dump(sb: StringBuilder) {
            val db = readableDatabase
            db.beginTransactionNonExclusive()

            // iterate through all tables
            db.query("sqlite_master", arrayOf("name"), "type='table'", null, null, null, null).use { cursorTables ->
                while (cursorTables.moveToNext()) {
                    val table = cursorTables.getString(0)
                    sb.append(table).append("\n")
                    db.query(table, null, null, null, null, null, null).use { cursor ->
                        // print columns
                        val cols = cursor.columnCount
                        sb.append("\t| ")
                        for (i in 0 .. cols-1)
                            sb  .append(" ")
                                .append(cursor.getColumnName(i))
                                .append(" |")
                        sb.append("\n")

                        // print rows
                        while (cursor.moveToNext()) {
                            sb.append("\t| ")
                            for (i in 0 .. cols-1) {
                                sb.append(" ")
                                try {
                                    val value = cursor.getString(i)
                                    if (value != null)
                                        sb.append(value
                                                .replace("\r", "<CR>")
                                                .replace("\n", "<LF>"))
                                    else
                                        sb.append("<null>")

                                } catch (e: SQLiteException) {
                                    sb.append("<unprintable>")
                                }
                                sb.append(" |")
                            }
                            sb.append("\n")
                        }
                        sb.append("----------\n")
                    }
                }
                db.endTransaction()
            }
        }
    }

}
