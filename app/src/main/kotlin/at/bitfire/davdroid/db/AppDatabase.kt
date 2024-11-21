/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteQueryBuilder
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.database.getStringOrNull
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.ProvidedAutoMigrationSpec
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TextTable
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.ui.AccountsActivity
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.ical4android.util.DateUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.Writer
import java.util.logging.Logger
import javax.inject.Singleton

@Suppress("ClassName")
@Database(entities = [
    Service::class,
    HomeSet::class,
    Collection::class,
    Principal::class,
    SyncStats::class,
    WebDavDocument::class,
    WebDavMount::class
], exportSchema = true, version = 16, autoMigrations = [
    AutoMigration(from = 9, to = 10),
    AutoMigration(from = 10, to = 11),
    AutoMigration(from = 11, to = 12, spec = AppDatabase.AutoMigration11_12::class),
    AutoMigration(from = 12, to = 13),
    AutoMigration(from = 13, to = 14),
    AutoMigration(from = 14, to = 15)
])
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {

    @Module
    @InstallIn(SingletonComponent::class)
    object AppDatabaseModule {
        @Provides
        @Singleton
        fun appDatabase(
            @ApplicationContext context: Context,
            notificationRegistry: NotificationRegistry
        ): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "services.db")
                .addMigrations(*migrations)
                .addAutoMigrationSpec(AutoMigration11_12(context))
                .fallbackToDestructiveMigration()   // as a last fallback, recreate database instead of crashing
                .addCallback(object: Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_DATABASE_CORRUPTED) {
                            val launcherIntent = Intent(context, AccountsActivity::class.java)
                            NotificationCompat.Builder(context, notificationRegistry.CHANNEL_GENERAL)
                                .setSmallIcon(R.drawable.ic_warning_notify)
                                .setContentTitle(context.getString(R.string.database_destructive_migration_title))
                                .setContentText(context.getString(R.string.database_destructive_migration_text))
                                .setCategory(NotificationCompat.CATEGORY_ERROR)
                                .setContentIntent(PendingIntent.getActivity(context, 0, launcherIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                                .setAutoCancel(true)
                                .build()
                        }

                        // remove all accounts because they're unfortunately useless without database
                        val am = AccountManager.get(context)
                        for (account in am.getAccountsByType(context.getString(R.string.account_type)))
                            am.removeAccountExplicitly(account)
                    }
                })
                .build()
    }

    // auto migrations

    @ProvidedAutoMigrationSpec
    @DeleteColumn(tableName = "collection", columnName = "owner")
    class AutoMigration11_12(val context: Context): AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            Logger.getGlobal().info("Database update to v12, refreshing services to get display names of owners")
            db.query("SELECT id FROM service", arrayOf()).use { cursor ->
                while (cursor.moveToNext()) {
                    val serviceId = cursor.getLong(0)
                    RefreshCollectionsWorker.enqueue(context, serviceId)
                }
            }
        }
    }


    companion object {

        // automatic migrations

        @VisibleForTesting
        val autoMigrationSpecs: List<(Context) -> AutoMigrationSpec> = listOf(
            { AutoMigration11_12(it) }
        )

        // manual migrations

        val migrations: Array<Migration> = arrayOf(
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // The timezone column has been removed, now it's timezoneId.
                    // First, create the new column to store the migrated values.
                    db.execSQL("ALTER TABLE collection ADD COLUMN timezoneId TEXT DEFAULT NULL")
                    // Now, fetch all the timezone values.
                    db.query("SELECT id, timezone FROM collection").use { cursor ->
                        while (cursor.moveToNext()) {
                            val id: Long = cursor.getLong(0)
                            val timezone: String = cursor.getString(1) ?: continue
                            val vTimeZone = DateUtils.parseVTimeZone(timezone)
                            val timezoneId = vTimeZone.timeZoneId.value
                            db.execSQL("UPDATE collection SET timezoneId=? WHERE id=?", arrayOf(timezoneId, id))
                        }
                    }
                    // DROP COLUMN is available since SQLite 3.35.0, which is available since API 34,
                    // in older versions just keep the timezone column
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        db.execSQL("ALTER TABLE collection DROP COLUMN timezone")
                    }
                }
            },

            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE syncstats (" +
                            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                            "collectionId INTEGER NOT NULL REFERENCES collection(id) ON DELETE CASCADE," +
                            "authority TEXT NOT NULL," +
                            "lastSync INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX index_syncstats_collectionId_authority ON syncstats(collectionId, authority)")

                    db.execSQL("CREATE INDEX index_collection_url ON collection(url)")
                }
            },

            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE homeset ADD COLUMN personal INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE collection ADD COLUMN homeSetId INTEGER DEFAULT NULL REFERENCES homeset(id) ON DELETE SET NULL")
                    db.execSQL("ALTER TABLE collection ADD COLUMN owner TEXT DEFAULT NULL")
                    db.execSQL("CREATE INDEX index_collection_homeSetId_type ON collection(homeSetId, type)")
                }
            },

            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE homeset ADD COLUMN privBind INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE homeset ADD COLUMN displayName TEXT DEFAULT NULL")
                }
            },

            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val sql = arrayOf(
                        // migrate "services" to "service": rename columns, make id NOT NULL
                        "CREATE TABLE service(" +
                                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                                "accountName TEXT NOT NULL," +
                                "type TEXT NOT NULL," +
                                "principal TEXT DEFAULT NULL" +
                                ")",
                        "CREATE UNIQUE INDEX index_service_accountName_type ON service(accountName, type)",
                        "INSERT INTO service(id, accountName, type, principal) SELECT _id, accountName, service, principal FROM services",
                        "DROP TABLE services",

                        // migrate "homesets" to "homeset": rename columns, make id NOT NULL
                        "CREATE TABLE homeset(" +
                                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                                "serviceId INTEGER NOT NULL," +
                                "url TEXT NOT NULL," +
                                "FOREIGN KEY (serviceId) REFERENCES service(id) ON DELETE CASCADE" +
                                ")",
                        "CREATE UNIQUE INDEX index_homeset_serviceId_url ON homeset(serviceId, url)",
                        "INSERT INTO homeset(id, serviceId, url) SELECT _id, serviceID, url FROM homesets",
                        "DROP TABLE homesets",

                        // migrate "collections" to "collection": rename columns, make id NOT NULL
                        "CREATE TABLE collection(" +
                                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                                "serviceId INTEGER NOT NULL," +
                                "type TEXT NOT NULL," +
                                "url TEXT NOT NULL," +
                                "privWriteContent INTEGER NOT NULL DEFAULT 1," +
                                "privUnbind INTEGER NOT NULL DEFAULT 1," +
                                "forceReadOnly INTEGER NOT NULL DEFAULT 0," +
                                "displayName TEXT DEFAULT NULL," +
                                "description TEXT DEFAULT NULL," +
                                "color INTEGER DEFAULT NULL," +
                                "timezone TEXT DEFAULT NULL," +
                                "supportsVEVENT INTEGER DEFAULT NULL," +
                                "supportsVTODO INTEGER DEFAULT NULL," +
                                "supportsVJOURNAL INTEGER DEFAULT NULL," +
                                "source TEXT DEFAULT NULL," +
                                "sync INTEGER NOT NULL DEFAULT 0," +
                                "FOREIGN KEY (serviceId) REFERENCES service(id) ON DELETE CASCADE" +
                                ")",
                        "CREATE INDEX index_collection_serviceId_type ON collection(serviceId,type)",
                        "INSERT INTO collection(id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, displayName, description, color, timezone, supportsVEVENT, supportsVTODO, source, sync) " +
                                "SELECT _id, serviceID, type, url, privWriteContent, privUnbind, forceReadOnly, displayName, description, color, timezone, supportsVEVENT, supportsVTODO, source, sync FROM collections",
                        "DROP TABLE collections"
                    )
                    sql.forEach { db.execSQL(it) }
                }
            },

            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE collections ADD COLUMN privWriteContent INTEGER DEFAULT 0 NOT NULL")
                    db.execSQL("UPDATE collections SET privWriteContent=NOT readOnly")

                    db.execSQL("ALTER TABLE collections ADD COLUMN privUnbind INTEGER DEFAULT 0 NOT NULL")
                    db.execSQL("UPDATE collections SET privUnbind=NOT readOnly")

                    // there's no DROP COLUMN in SQLite, so just keep the "readOnly" column
                }
            },

            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE collections ADD COLUMN forceReadOnly INTEGER DEFAULT 0 NOT NULL")
                }
            },

            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // We don't have access to the context in a Room migration now, so
                    // we will just drop those settings from old DAVx5 versions.
                    Logger.getGlobal().warning("Dropping settings distrustSystemCerts and overrideProxy*")
                }
            },

            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE collections ADD COLUMN type TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE collections ADD COLUMN source TEXT DEFAULT NULL")
                    db.execSQL("UPDATE collections SET type=(" +
                            "SELECT CASE service WHEN ? THEN ? ELSE ? END " +
                            "FROM services WHERE _id=collections.serviceID" +
                            ")",
                            arrayOf("caldav", "CALENDAR", "ADDRESS_BOOK"))
                }
            }
        )

    }


    // DAOs

    abstract fun serviceDao(): ServiceDao
    abstract fun homeSetDao(): HomeSetDao
    abstract fun collectionDao(): CollectionDao
    abstract fun principalDao(): PrincipalDao
    abstract fun syncStatsDao(): SyncStatsDao
    abstract fun webDavDocumentDao(): WebDavDocumentDao
    abstract fun webDavMountDao(): WebDavMountDao


    // helpers

    fun dump(writer: Writer, ignoreTables: Array<String>) {
        val db = openHelper.readableDatabase
        db.beginTransactionNonExclusive()

        // iterate through all tables
        db.query(SQLiteQueryBuilder.buildQueryString(false, "sqlite_master", arrayOf("name"), "type='table'", null, null, null, null)).use { cursorTables ->
            while (cursorTables.moveToNext()) {
                val tableName = cursorTables.getString(0)
                if (ignoreTables.contains(tableName)) {
                    writer.append("$tableName: ")
                    db.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
                        if (cursor.moveToNext())
                            writer.append("${cursor.getInt(0)} row(s), data not listed here\n\n")
                    }
                } else {
                    writer.append("$tableName\n")
                    db.query("SELECT * FROM $tableName").use { cursor ->
                        val table = TextTable(*cursor.columnNames)
                        val cols = cursor.columnCount
                        // print rows
                        while (cursor.moveToNext()) {
                            val values = Array(cols) { idx -> cursor.getStringOrNull(idx) }
                            table.addLine(*values)
                        }
                        writer.append(table.toString())
                    }
                }
            }
            db.endTransaction()
        }
    }

}