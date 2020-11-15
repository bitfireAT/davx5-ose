package at.bitfire.davdroid.model

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class AppDatabaseTest {

    val TEST_DB = "test"

    @Rule
    @JvmField
    val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory()
    )


    @Test
    fun testAllMigrations() {
        helper.createDatabase(TEST_DB, 8).close()

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*AppDatabase.migrations).build().apply {
            openHelper.writableDatabase
            close()
        }
    }

}