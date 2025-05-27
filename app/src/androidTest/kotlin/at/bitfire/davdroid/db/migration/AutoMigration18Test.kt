/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

@HiltAndroidTest
class AutoMigration18Test : DatabaseMigrationTest(toVersion = 18) {

    @Test
    fun testMigration_AllAuthorities() = testMigration(
        prepare = { db ->
            // Insert service and collection to respect relation constraints
            db.execSQL("INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)", arrayOf(1, "test", 1))
            listOf(1L, 2L, 3L).forEach { id ->
                db.execSQL(
                    "INSERT INTO collection (id, serviceId, url, type, privWriteContent, privUnbind, forceReadOnly, sync) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(id, 1, "https://example.com/$id", 1, 1, 1, 0, 1)
                )
            }
            // Insert a some syncstats with authorities and lastSync times
            val syncstats = listOf(
                Entry(1, 1, "com.android.contacts", 1000),
                Entry(2, 1, "com.android.calendar", 1000),
                Entry(3, 1, "org.dmfs.tasks", 1000),
                Entry(4, 1, "org.tasks.opentasks", 2000),
                Entry(5, 1, "at.techbee.jtx.provider", 3000), // highest lastSync for collection 1
                Entry(6, 1, "unknown.authority", 1000),

                Entry(7, 2, "org.dmfs.tasks", 1000),
                Entry(8, 2, "org.tasks.opentasks", 2000), // highest lastSync for collection 2

                Entry(9, 3, "org.tasks.opentasks", 1000),
            )
            syncstats.forEach { (id, collectionId, authority, lastSync) ->
                db.execSQL(
                    "INSERT INTO syncstats (id, collectionId, authority, lastSync) VALUES (?, ?, ?, ?)",
                    arrayOf(id, collectionId, authority, lastSync)
                )
            }
        },
        validate = { db ->
            db.query("SELECT id, collectionId, dataType FROM syncstats ORDER BY id").use { cursor ->
                val found = mutableListOf<Entry>()
                db.query("SELECT id, collectionId, dataType FROM syncstats ORDER BY id").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val colIdx = cursor.getColumnIndex("collectionId")
                    val typeIdx = cursor.getColumnIndex("dataType")
                    while (cursor.moveToNext())
                        found.add(
                            Entry(cursor.getInt(idIdx), cursor.getLong(colIdx), cursor.getString(typeIdx))
                        )
                }

                // Expect one TASKS row per collection (collections 1, 2, 3), and the others
                assertEquals(
                    listOf(
                        Entry(1, 1, "CONTACTS"),
                        Entry(2, 1, "EVENTS"),
                        Entry(5, 1, "TASKS"),   // highest lastSync TASK for collection 1 is JTX Board
                        Entry(6, 1, "UNKNOWN"),
                        Entry(8, 2, "TASKS"),   // highest lastSync TASK for collection 2
                        Entry(9, 3, "TASKS"),   // only TASK for collection 3
                    ), found
                )
            }
        }
    )

    data class Entry(
        val id: Int,
        val collectionId: Long,
        val dataType: String? = null,
        val lastSync: Long? = null
    )
}
