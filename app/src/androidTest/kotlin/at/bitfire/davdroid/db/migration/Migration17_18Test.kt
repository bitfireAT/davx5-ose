/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import at.bitfire.davdroid.db.Service
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("ClassName")
@HiltAndroidTest
class Migration17_18Test: DatabaseMigrationTest(fromVersion = 16, toVersion = 18) {

    @Test
    fun testMigrate_WithServices() = testMigration(
        prepare = { db ->
            db.execSQL(
                "INSERT INTO service (accountName, type) VALUES (?, ?)",
                arrayOf("test1", Service.Companion.TYPE_CALDAV)
            )
            db.execSQL(
                "INSERT INTO service (accountName, type) VALUES (?, ?)",
                arrayOf("test1", Service.Companion.TYPE_CARDDAV)
            )

            db.execSQL(
                "INSERT INTO service (accountName, type) VALUES (?, ?)",
                arrayOf("test2", Service.Companion.TYPE_CALDAV)
            )

            db.execSQL(
                "INSERT INTO service (accountName, type) VALUES (?, ?)",
                arrayOf("test3", Service.Companion.TYPE_CARDDAV)
            )
        }
    ) { db ->
        db.query("SELECT name FROM account ORDER BY name").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("test1", cursor.getString(0))

            assertTrue(cursor.moveToNext())
            assertEquals("test2", cursor.getString(0))

            assertTrue(cursor.moveToNext())
            assertEquals("test3", cursor.getString(0))

            assertFalse(cursor.moveToNext())
        }
    }

}