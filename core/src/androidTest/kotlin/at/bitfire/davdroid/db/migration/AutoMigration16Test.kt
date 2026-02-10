/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import at.bitfire.davdroid.db.Collection.Companion.TYPE_CALENDAR
import at.bitfire.davdroid.db.Service
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@HiltAndroidTest
class AutoMigration16Test: DatabaseMigrationTest(toVersion = 16) {

    @Test
    fun testMigrate_WithTimeZone() = testMigration(
        prepare = { db ->
            val minimalVTimezone = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:DAVx5
                BEGIN:VTIMEZONE
                TZID:America/New_York
                END:VTIMEZONE
                END:VCALENDAR
            """.trimIndent()
            db.execSQL(
                "INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)",
                arrayOf<Any>(1, "test", Service.Companion.TYPE_CALDAV)
            )
            db.execSQL(
                "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync, timezone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(1, 1, TYPE_CALENDAR, "https://example.com", true, true, false, false, minimalVTimezone)
            )
        }
    ) { db ->
        db.query("SELECT timezoneId FROM collection WHERE id=1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("America/New_York", cursor.getString(0))
        }
    }

    @Test
    fun testMigrate_WithTimeZone_Unparseable() = testMigration(
        prepare = { db ->
            db.execSQL(
                "INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)",
                arrayOf<Any>(1, "test", Service.Companion.TYPE_CALDAV)
            )
            db.execSQL(
                "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync, timezone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(1, 1, TYPE_CALENDAR, "https://example.com", true, true, false, false, "Some Garbage Content")
            )
        }
    ) { db ->
        db.query("SELECT timezoneId FROM collection WHERE id=1").use { cursor ->
            cursor.moveToFirst()
            assertNull(cursor.getString(0))
        }
    }

    @Test
    fun testMigrate_WithoutTimezone() = testMigration(
        prepare = { db ->
            db.execSQL(
                "INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)",
                arrayOf<Any>(1, "test", Service.Companion.TYPE_CALDAV)
            )
            db.execSQL(
                "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(1, 1, TYPE_CALENDAR, "https://example.com", true, true, false, false)
            )
        }
    ) { db ->
        db.query("SELECT timezoneId FROM collection WHERE id=1").use { cursor ->
            cursor.moveToFirst()
            assertNull(cursor.getString(0))
        }
    }

}