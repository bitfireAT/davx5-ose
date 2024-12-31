/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.ProvidedAutoMigrationSpec
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.ical4android.util.DateUtils

/**
 * The timezone column has been renamed to timezoneId, but still contains the VTIMEZONE.
 * So we need to parse the VTIMEZONE, extract the timezone ID and save it back.
 */
@ProvidedAutoMigrationSpec
@RenameColumn(tableName = "collection", fromColumnName = "timezone", toColumnName = "timezoneId")
class AutoMigration16: AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.query("SELECT id, timezoneId FROM collection").use { cursor ->
            while (cursor.moveToNext()) {
                val id: Long = cursor.getLong(0)
                val timezoneDef: String = cursor.getString(1) ?: continue
                val vTimeZone = DateUtils.parseVTimeZone(timezoneDef)
                val timezoneId = vTimeZone?.timeZoneId?.value
                db.execSQL("UPDATE collection SET timezoneId=? WHERE id=?", arrayOf(timezoneId, id))
            }
        }
    }

}