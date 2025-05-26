/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteQueryBuilder
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.database.getStringOrNull
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TextTable
import at.bitfire.davdroid.db.migration.AutoMigration12
import at.bitfire.davdroid.db.migration.AutoMigration16
import at.bitfire.davdroid.ui.MainActivity
import at.bitfire.davdroid.ui.NotificationRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.Writer
import javax.inject.Singleton

@Database(entities = [
    Service::class,
    HomeSet::class,
    Collection::class,
    Principal::class,
    SyncStats::class,
    WebDavDocument::class,
    WebDavMount::class
], exportSchema = true, version = 17, autoMigrations = [
    AutoMigration(from = 16, to = 17),      // collection: add VAPID key
    AutoMigration(from = 15, to = 16, spec = AutoMigration16::class),
    AutoMigration(from = 14, to = 15),
    AutoMigration(from = 13, to = 14),
    AutoMigration(from = 12, to = 13),
    AutoMigration(from = 11, to = 12, spec = AutoMigration12::class),
    AutoMigration(from = 10, to = 11),
    AutoMigration(from = 9, to = 10)
])
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {

    @Module
    @InstallIn(SingletonComponent::class)
    object AppDatabaseModule {

        @Provides
        @Singleton
        fun appDatabase(
            autoMigrations: Set<@JvmSuppressWildcards AutoMigrationSpec>,
            @ApplicationContext context: Context,
            manualMigrations: Set<@JvmSuppressWildcards Migration>,
            notificationRegistry: NotificationRegistry
        ): AppDatabase = Room
            .databaseBuilder(context, AppDatabase::class.java, "services.db")
            .addMigrations(*manualMigrations.toTypedArray())
            .apply {
                for (spec in autoMigrations)
                    addAutoMigrationSpec(spec)
            }
            .fallbackToDestructiveMigration()   // as a last fallback, recreate database instead of crashing
            .addCallback(object: Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_DATABASE_CORRUPTED) {
                        val launcherIntent = Intent(context, MainActivity::class.java)
                        NotificationCompat.Builder(context, notificationRegistry.CHANNEL_GENERAL)
                            .setSmallIcon(R.drawable.ic_warning_notify)
                            .setContentTitle(context.getString(R.string.database_destructive_migration_title))
                            .setContentText(context.getString(R.string.database_destructive_migration_text))
                            .setCategory(NotificationCompat.CATEGORY_ERROR)
                            .setContentIntent(
                                TaskStackBuilder.create(context)
                                    .addNextIntent(launcherIntent)
                                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            )
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