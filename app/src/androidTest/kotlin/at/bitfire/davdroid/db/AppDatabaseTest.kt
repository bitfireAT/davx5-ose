/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class AppDatabaseTest {

    val TEST_DB = "test"

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Rule
    @JvmField
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(), // no auto migrations until v8
        FrameworkSQLiteOpenHelperFactory()
    )


    @Test
    fun testAllMigrations() {
        // DB schema is available since version 8, so create DB with v8
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

}