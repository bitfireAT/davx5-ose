/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.db.AppDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Helper for testing the database migration from [toVersion] - 1 to [toVersion].
 *
 * @param toVersion     The target version to migrate to.
 */
abstract class DatabaseMigrationTest(
    private val toVersion: Int
) {

    @Inject
    lateinit var autoMigrations: Set<@JvmSuppressWildcards AutoMigrationSpec>

    @Inject
    lateinit var manualMigrations: Set<@JvmSuppressWildcards Migration>

    @get:Rule
    val hiltRule = HiltAndroidRule(this)


    @Before
    fun setup() {
        hiltRule.inject()
    }


    /**
     * Used for testing the migration process from [toVersion]-1 to [toVersion].
     *
     * @param prepare      Callback to prepare the database. Will be run with database schema in version [toVersion] - 1.
     * @param validate     Callback to validate the migration result. Will be run with database schema in version [toVersion].
     */
    protected fun testMigration(
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
        val dbName = "test"
        helper.createDatabase(dbName, version = toVersion - 1).apply {
            // Foreign key constraint enforcement is disabled by default in SQLite and needs to
            // be enabled per connection. For production code room does that for us - in tests we
            // need to enable it ourselves. We could enable foreign key constraint enforcement here,
            // but it's more practical for the tests to not do so.
            // execSQL("PRAGMA foreign_keys=ON;");
            prepare(this)
            close()
        }

        // Re-open the database with the new version and provide all the migrations.
        val db = helper.runMigrationsAndValidate(
            name = dbName,
            version = toVersion,
            validateDroppedTables = true,
            migrations = manualMigrations.toTypedArray()
        )

        validate(db)
    }

}