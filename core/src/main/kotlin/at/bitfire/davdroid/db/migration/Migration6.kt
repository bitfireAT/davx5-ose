/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.migration.Migration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

val Migration6 = Migration(5, 6) { db ->
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

@Module
@InstallIn(SingletonComponent::class)
internal object Migration6Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration6
}