/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AppDatabaseTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
    }


    @Test
    fun testAllMigrations() {
        // DB schema is available since version 8, so create DB with v8
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            listOf(), // no auto migrations until v8
            FrameworkSQLiteOpenHelperFactory()
        )
        helper.createDatabase(TEST_DB, 8).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            // manual migrations
            .addMigrations(*AppDatabase.migrations)
            // auto-migrations that need to be specified explicitly
            .addAutoMigrationSpec(AppDatabase.AutoMigration11_12(context))
            .build()
        try {
            // open (with version 8) + migrate (to current version) database

            db.openHelper.writableDatabase
        } finally {
            db.close()
        }
    }


    companion object {
        const val TEST_DB = "test"
    }

}