/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteQueryBuilder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.database.getStringOrNull
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TextTable
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.AccountsActivity
import at.bitfire.davdroid.ui.NotificationUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.Writer
import javax.inject.Singleton

@Suppress("ClassName")
@Database(entities = [
    Service::class,
    HomeSet::class,
    Collection::class,
    SyncStats::class,
    WebDavDocument::class,
    WebDavMount::class
], exportSchema = true, version = 11, autoMigrations = [
    AutoMigration(from = 9, to = 10),
    AutoMigration(from = 10, to = 11)
])
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {

    @Module
    @InstallIn(SingletonComponent::class)
    object AppDatabaseModule {
        @Provides
        @Singleton
        fun appDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "services.db")
                .addMigrations(*migrations)
                .fallbackToDestructiveMigration()   // as a last fallback, recreate database instead of crashing
                .addCallback(object: Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        val nm = NotificationManagerCompat.from(context)
                        val launcherIntent = Intent(context, AccountsActivity::class.java)
                        val notify = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_GENERAL)
                            .setSmallIcon(R.drawable.ic_warning_notify)
                            .setContentTitle(context.getString(R.string.database_destructive_migration_title))
                            .setContentText(context.getString(R.string.database_destructive_migration_text))
                            .setCategory(NotificationCompat.CATEGORY_ERROR)
                            .setContentIntent(PendingIntent.getActivity(context, 0, launcherIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                            .setAutoCancel(true)
                            .build()
                        nm.notify(NotificationUtils.NOTIFY_DATABASE_CORRUPTED, notify)

                        // remove all accounts because they're unfortunately useless without database
                        val am = AccountManager.get(context)
                        for (account in am.getAccountsByType(context.getString(R.string.account_type)))
                            am.removeAccount(account, null, null)
                    }
                })
                .build()
    }

    companion object {

        // migrations

        val migrations: Array<Migration> = arrayOf(
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
                    Logger.log.warning("Dropping settings distrustSystemCerts and overrideProxy*")

                    /*val edit = PreferenceManager.getDefaultSharedPreferences(context).edit()
                    try {
                        db.query("settings", arrayOf("setting", "value"), null, null, null, null, null).use { cursor ->
                            while (cursor.moveToNext()) {
                                when (cursor.getString(0)) {
                                    "distrustSystemCerts" -> edit.putBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, cursor.getInt(1) != 0)
                                    "overrideProxy" -> edit.putBoolean(App.OVERRIDE_PROXY, cursor.getInt(1) != 0)
                                    "overrideProxyHost" -> edit.putString(App.OVERRIDE_PROXY_HOST, cursor.getString(1))
                                    "overrideProxyPort" -> edit.putInt(App.OVERRIDE_PROXY_PORT, cursor.getInt(1))

                                    StartupDialogFragment.HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED ->
                                        edit.putBoolean(StartupDialogFragment.HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED, cursor.getInt(1) != 0)
                                    StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED ->
                                        edit.putBoolean(StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED, cursor.getInt(1) != 0)
                                }
                            }
                        }
                        db.execSQL("DROP TABLE settings")
                    } finally {
                        edit.apply()
                    }*/
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