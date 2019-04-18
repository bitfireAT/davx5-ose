package at.bitfire.davdroid.model

import android.content.Context
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteQueryBuilder
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.davdroid.log.Logger

@Suppress("ClassName")
@Database(entities = [
    Service::class,
    HomeSet::class,
    Collection::class
], version = 6)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {

    abstract fun serviceDao(): ServiceDao
    abstract fun homeSetDao(): HomeSetDao
    abstract fun collectionDao(): CollectionDao

    companion object {

        private var INSTANCE: AppDatabase? = null

        @Synchronized
        fun getInstance(context: Context): AppDatabase {
            INSTANCE?.let { return it }

            val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "services.db")
                    .addMigrations(
                            Migration1_2,
                            Migration2_3,
                            Migration3_4,
                            Migration4_5,
                            Migration5_6
                    )
                    .fallbackToDestructiveMigration()   // as a last fallback, recreate database instead of crashing
                    .build()
            INSTANCE = db

            return db
        }

    }

    fun dump(sb: StringBuilder) {
        val db = openHelper.readableDatabase
        db.beginTransactionNonExclusive()

        // iterate through all tables
        db.query(SQLiteQueryBuilder.buildQueryString(false, "sqlite_master", arrayOf("name"), "type='table'", null, null, null, null)).use { cursorTables ->
            while (cursorTables.moveToNext()) {
                val table = cursorTables.getString(0)
                sb.append(table).append("\n")
                db.query("SELECT * FROM $table").use { cursor ->
                    // print columns
                    val cols = cursor.columnCount
                    sb.append("\t| ")
                    for (i in 0 until cols)
                        sb  .append(" ")
                                .append(cursor.getColumnName(i))
                                .append(" |")
                    sb.append("\n")

                    // print rows
                    while (cursor.moveToNext()) {
                        sb.append("\t| ")
                        for (i in 0 until cols) {
                            sb.append(" ")
                            try {
                                val value = cursor.getString(i)
                                if (value != null)
                                    sb.append(value
                                            .replace("\r", "<CR>")
                                            .replace("\n", "<LF>"))
                                else
                                    sb.append("<null>")

                            } catch (e: SQLiteException) {
                                sb.append("<unprintable>")
                            }
                            sb.append(" |")
                        }
                        sb.append("\n")
                    }
                    sb.append("----------\n")
                }
            }
            db.endTransaction()
        }
    }


    // migrations

    object Migration5_6: Migration(5, 6) {
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
    }

    object Migration4_5: Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE collections ADD COLUMN privWriteContent INTEGER DEFAULT 0 NOT NULL")
            db.execSQL("UPDATE collections SET privWriteContent=NOT readOnly")

            db.execSQL("ALTER TABLE collections ADD COLUMN privUnbind INTEGER DEFAULT 0 NOT NULL")
            db.execSQL("UPDATE collections SET privUnbind=NOT readOnly")

            // there's no DROP COLUMN in SQLite, so just keep the "readOnly" column
        }
    }

    object Migration3_4: Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE collections ADD COLUMN forceReadOnly INTEGER DEFAULT 0 NOT NULL")
        }
    }

    object Migration2_3: Migration(2, 3) {
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
    }

    object Migration1_2: Migration(1, 2) {
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

}