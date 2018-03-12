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
import android.preference.PreferenceManager
import at.bitfire.davdroid.App
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.StartupDialogFragment
import java.util.logging.Level

class ServiceDB {

    object Services {
        const val _TABLE = "services"
        const val ID = "_id"
        const val ACCOUNT_NAME = "accountName"
        const val SERVICE = "service"
        const val PRINCIPAL = "principal"

        // allowed values for SERVICE column
        const val SERVICE_CALDAV = "caldav"
        const val SERVICE_CARDDAV = "carddav"
    }

    object HomeSets {
        const val _TABLE = "homesets"
        const val ID = "_id"
        const val SERVICE_ID = "serviceID"
        const val URL = "url"
    }

    object Collections {
        const val _TABLE = "collections"
        const val ID = "_id"
        const val TYPE = "type"
        const val SERVICE_ID = "serviceID"
        const val URL = "url"
        const val READ_ONLY = "readOnly"
        const val FORCE_READ_ONLY = "forceReadOnly"
        const val DISPLAY_NAME = "displayName"
        const val DESCRIPTION = "description"
        const val COLOR = "color"
        const val TIME_ZONE = "timezone"
        const val SUPPORTS_VEVENT = "supportsVEVENT"
        const val SUPPORTS_VTODO = "supportsVTODO"
        const val SOURCE = "source"
        const val SYNC = "sync"
    }

    companion object {

        fun onRenameAccount(db: SQLiteDatabase, oldName: String, newName: String) {
            val values = ContentValues(1)
            values.put(Services.ACCOUNT_NAME, newName)
            db.updateWithOnConflict(Services._TABLE, values, Services.ACCOUNT_NAME + "=?", arrayOf(oldName), SQLiteDatabase.CONFLICT_REPLACE)
        }

    }


    class OpenHelper(
            val context: Context
    ): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), AutoCloseable {

        companion object {
            const val DATABASE_NAME = "services.db"
            const val DATABASE_VERSION = 4
        }

        override fun onConfigure(db: SQLiteDatabase) {
            setWriteAheadLoggingEnabled(true)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            Logger.log.info("Creating database " + db.path)

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
                    "${Collections.FORCE_READ_ONLY} INTEGER DEFAULT 0 NOT NULL," +
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
            for (upgradeFrom in oldVersion until newVersion) {
                val upgradeTo = upgradeFrom + 1
                Logger.log.info("Upgrading database from version $upgradeFrom to $upgradeTo")
                try {
                    val upgradeProc = this::class.java.getDeclaredMethod("upgrade_${upgradeFrom}_$upgradeTo", SQLiteDatabase::class.java)
                    upgradeProc.invoke(this, db)
                } catch(e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't upgrade database", e)
                }
            }
        }

        @Suppress("unused")
        private fun upgrade_3_4(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE ${Collections._TABLE} ADD COLUMN ${Collections.FORCE_READ_ONLY} INTEGER DEFAULT 0 NOT NULL")
        }

        @Suppress("unused")
        private fun upgrade_2_3(db: SQLiteDatabase) {
            val edit = PreferenceManager.getDefaultSharedPreferences(context).edit()
            try {
                db.query("settings", arrayOf("setting", "value"), null, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        when (cursor.getString(0)) {
                            "distrustSystemCerts" -> edit.putBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, cursor.getInt(1) != 0)
                            "overrideProxy" -> edit.putBoolean(App.OVERRIDE_PROXY, cursor.getInt(1) != 0)
                            "overrideProxyHost" -> edit.putString(App.OVERRIDE_PROXY_HOST, cursor.getString(1))
                            "overrideProxyPort" -> edit.putInt(App.OVERRIDE_PROXY_PORT, cursor.getInt(1))

                            StartupDialogFragment.HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED ->
                                edit.putBoolean(StartupDialogFragment.HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED, cursor.getInt(1) != 0)
                            StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED ->
                                edit.putBoolean(StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED, cursor.getInt(1) != 0)
                        }
                    }
                }
                db.execSQL("DROP TABLE settings")
            } finally {
                edit.apply()
            }
        }

        @Suppress("unused")
        private fun upgrade_1_2(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE ${Collections._TABLE} ADD COLUMN ${Collections.TYPE} TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ${Collections._TABLE} ADD COLUMN ${Collections.SOURCE} TEXT NULL")
            db.execSQL("UPDATE ${Collections._TABLE} SET ${Collections.TYPE}=(" +
                    "SELECT CASE ${Services.SERVICE} WHEN ? THEN ? ELSE ? END " +
                    "FROM ${Services._TABLE} WHERE ${Services.ID}=${Collections._TABLE}.${Collections.SERVICE_ID}" +
                    ")",
                    arrayOf(Services.SERVICE_CALDAV, CollectionInfo.Type.CALENDAR, CollectionInfo.Type.ADDRESS_BOOK))
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
                        for (i in 0 until cols)
                            sb  .append(" ")
                                .append(cursor.getColumnName(i))
                                .append(" |")
                        sb.append("\n")

                        // print rows
                        while (cursor.moveToNext()) {
                            sb.append("\t| ")
                            for (i in 0 until cols) {
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