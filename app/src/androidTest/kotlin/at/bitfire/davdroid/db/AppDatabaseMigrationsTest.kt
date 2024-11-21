/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.content.Context
import androidx.room.Room
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AppDatabaseMigrationsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
    }


    private val autoMigrationSpecs by lazy { AppDatabase.getAutoMigrationSpecs(context) }

    val helper by lazy {
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            autoMigrationSpecs,
            FrameworkSQLiteOpenHelperFactory()
        )
    }

    /**
     * Used for testing the migration process between two versions.
     * @param fromVersion The version from which to start testing.
     * @param toVersion The target version to test
     * @param setup Run here all the SQL queries that prepare the database with the data required
     * to perform the migration process.
     * @param assertionsBlock Run here all the assertions for [AppDatabase] to make sure the
     * migration was performed correctly.
     */
    private fun testMigration(
        fromVersion: Int,
        toVersion: Int,
        setup: SupportSQLiteDatabase.() -> Unit,
        assertionsBlock: (AppDatabase) -> Unit
    ) {
        var db = helper.createDatabase(TEST_DB, fromVersion).apply {
            setup()

            // Prepare for the next version.
            close()
        }

        // Re-open the database with the new version and provide all the migrations.
        db = helper.runMigrationsAndValidate(TEST_DB, toVersion, true, *AppDatabase.migrations)

        // Once the migration process is complete, load AppDatabase
        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            // Include all manual migrations
            .addMigrations(*AppDatabase.migrations)
            // And all auto migration specs
            .apply {
                for (spec in autoMigrationSpecs) {
                    addAutoMigrationSpec(spec)
                }
            }
            .build()
        assertionsBlock(database)
    }

    /**
     * Test migrations from full VTIMEZONE to just timezone ID
     */
    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun migrate15To16_WithTimezone() {
        testMigration(
            fromVersion = 15,
            toVersion = 16,
            setup = {
                val minimalVTimezone = """
                    BEGIN:VCALENDAR
                    VERSION:2.0
                    PRODID:DAVx5
                    BEGIN:VTIMEZONE
                    TZID:America/New_York
                    END:VTIMEZONE
                    END:VCALENDAR
                """.trimIndent()
                execSQL(
                    "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync, timezone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        1000, 0, TYPE_CALENDAR, "https://example.com", true, true, false, false, minimalVTimezone
                    )
                )
            }
        ) { database ->
            val collection = database.collectionDao().get(1000)
            assertEquals("America/New_York", collection?.timezoneId)
        }
    }

    /**
     * Test migrations from full VTIMEZONE to just timezone ID
     */
    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun migrate15To16_WithoutTimezone() {
        testMigration(
            fromVersion = 15,
            toVersion = 16,
            setup = {
                execSQL(
                    "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        1000, 0, TYPE_CALENDAR, "https://example.com", true, true, false, false
                    )
                )
            }
        ) { database ->
            val collection = database.collectionDao().get(1000)
            assertNotNull(collection)
            assertNull(collection?.timezoneId)
        }
    }


    companion object {
        const val TEST_DB = "test"
    }

}