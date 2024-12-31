/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class AppDatabaseTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var autoMigrations: Set<@JvmSuppressWildcards AutoMigrationSpec>

    @Inject
    lateinit var logger: Logger

    @Before
    fun setup() {
        hiltRule.inject()
    }


    /**
     * Creates a database with schema version 8 (the first exported one) and then migrates it to the latest version.
     */
    @Test
    fun testAllMigrations() {
        // Create DB with v8
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            listOf(), // no auto migrations until v8
            FrameworkSQLiteOpenHelperFactory()
        ).createDatabase(TEST_DB, 8).close()

        // open and migrate (to current version) database
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            // manual migrations
            .addMigrations(*AppDatabase.manualMigrations)
            // auto-migrations that need to be specified explicitly
            .apply {
                for (spec in autoMigrations)
                    addAutoMigrationSpec(spec)
            }
            .build()
            .openHelper.writableDatabase    // this will run all migrations
            .close()
    }


    companion object {
        const val TEST_DB = "test"
    }

}