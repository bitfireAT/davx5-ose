/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.content.Context
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.db.Collection.Companion.TYPE_CALENDAR
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AppDatabaseMigrationsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var autoMigrations: Set<@JvmSuppressWildcards AutoMigrationSpec>

    @Before
    fun setup() {
        hiltRule.inject()
    }


    /**
     * Used for testing the migration process between two versions.
     *
     * @param fromVersion  The version from which to start testing
     * @param toVersion    The target version to test
     * @param prepare      Callback to prepare the database. Will be run with database schema in version [fromVersion].
     * @param validate     Callback to validate the migration result. Will be run with database schema in version [toVersion].
     */
    private fun testMigration(
        fromVersion: Int,
        toVersion: Int,
        prepare: (SupportSQLiteDatabase) -> Unit,
        validate: (SupportSQLiteDatabase) -> Unit
    ) {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            autoMigrations.toList(),
            FrameworkSQLiteOpenHelperFactory()
        )

        // Prepare the database with the initial version.
        helper.createDatabase(TEST_DB, version = fromVersion).apply {
            prepare(this)
            close()
        }

        // Re-open the database with the new version and provide all the migrations.
        val db = helper.runMigrationsAndValidate(
            name = TEST_DB,
            version = toVersion,
            validateDroppedTables = true,
            migrations = AppDatabase.manualMigrations
        )

        validate(db)
    }


    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun migrate15To16_WithTimeZone() {
        testMigration(
            fromVersion = 15,
            toVersion = 16,
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
                    arrayOf(1, "test", Service.TYPE_CALDAV)
                )
                db.execSQL(
                    "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync, timezone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(1, 1, TYPE_CALENDAR, "https://example.com", true, true, false, false, minimalVTimezone)
                )
            }
        ) { db ->
            db.query("SELECT timezoneId FROM collection WHERE id=1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("America/New_York", cursor.getString(0))
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun migrate15To16_WithTimeZone_Unparseable() {
        testMigration(
            fromVersion = 15,
            toVersion = 16,
            prepare = { db ->
                db.execSQL(
                    "INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)",
                    arrayOf(1, "test", Service.TYPE_CALDAV)
                )
                db.execSQL(
                    "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync, timezone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(1, 1, TYPE_CALENDAR, "https://example.com", true, true, false, false, "Some Garbage Content")
                )
            }
        ) { db ->
            db.query("SELECT timezoneId FROM collection WHERE id=1").use { cursor ->
                cursor.moveToFirst()
                assertNull(cursor.getString(0))
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun migrate15To16_WithoutTimezone() {
        testMigration(
            fromVersion = 15,
            toVersion = 16,
            prepare = { db ->
                db.execSQL(
                    "INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)",
                    arrayOf(1, "test", Service.TYPE_CALDAV)
                )
                db.execSQL(
                    "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(1, 1, TYPE_CALENDAR, "https://example.com", true, true, false, false)
                )
            }
        ) { db ->
            db.query("SELECT timezoneId FROM collection WHERE id=1").use { cursor ->
                cursor.moveToFirst()
                assertNull(cursor.getString(0))
            }
        }
    }


    companion object {
        const val TEST_DB = "test"
    }

}