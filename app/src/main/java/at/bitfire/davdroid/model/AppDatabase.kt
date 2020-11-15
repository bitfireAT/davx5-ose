package at.bitfire.davdroid.model

import android.content.Context
import android.database.sqlite.SQLiteQueryBuilder
import androidx.core.database.getStringOrNull
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.davdroid.AndroidSingleton
import at.bitfire.davdroid.TextTable
import at.bitfire.davdroid.log.Logger
import java.io.Writer

@Suppress("ClassName")
@Database(entities = [
    Service::class,
    HomeSet::class,
    Collection::class
], exportSchema = true, version = 8)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {

    abstract fun serviceDao(): ServiceDao
    abstract fun homeSetDao(): HomeSetDao
    abstract fun collectionDao(): CollectionDao

    companion object: AndroidSingleton<AppDatabase>() {

        override fun createInstance(context: Context): AppDatabase =
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "services.db")
                    .addMigrations(*migrations)
                    .fallbackToDestructiveMigration()   // as a last fallback, recreate database instead of crashing
                    .build()


        // migrations

        val migrations: Array<Migration> = arrayOf(
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


    fun dump(writer: Writer) {
        val db = openHelper.readableDatabase
        db.beginTransactionNonExclusive()

        // iterate through all tables
        db.query(SQLiteQueryBuilder.buildQueryString(false, "sqlite_master", arrayOf("name"), "type='table'", null, null, null, null)).use { cursorTables ->
            while (cursorTables.moveToNext()) {
                val tableName = cursorTables.getString(0)

                writer.append("$tableName\n")
                db.query("SELECT * FROM $tableName").use { cursor ->
                    val table = TextTable(*cursor.columnNames)
                    val cols = cursor.columnCount
                    // print rows
                    while (cursor.moveToNext()) {
                        val values = Array<String?>(cols) { idx -> cursor.getStringOrNull(idx) }
                        table.addLine(*values)
                    }
                    writer.append(table.toString())
                }
            }
            db.endTransaction()
        }
    }

}