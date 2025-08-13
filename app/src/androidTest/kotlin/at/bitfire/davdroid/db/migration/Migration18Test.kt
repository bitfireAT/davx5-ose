/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class Migration18Test : DatabaseMigrationTest(toVersion = 18) {

    @Test
    fun testMigration_AllAuthorities() = testMigration(
        prepare = { db ->
            // Insert service and collection to respect relation constraints
            db.execSQL("INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)", arrayOf<Any?>(1, "test", 1))
            listOf(1L, 2L, 3L).forEach { id ->
                db.execSQL(
                    "INSERT INTO collection (id, serviceId, url, type, privWriteContent, privUnbind, forceReadOnly, sync) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(id, 1, "https://example.com/$id", 1, 1, 1, 0, 1)
                )
            }
            // Insert some syncstats with authorities and lastSync times
            val syncstats = listOf(
                Entry(1, 1, "com.android.contacts", 1000),
                Entry(2, 1, "com.android.calendar", 1000),
                Entry(3, 1, "org.dmfs.tasks", 1000),
                Entry(4, 1, "org.tasks.opentasks", 2000),
                Entry(5, 1, "at.techbee.jtx.provider", 3000), // highest lastSync for collection 1
                Entry(6, 1, "unknown.authority", 1000),  // ignored

                Entry(7, 2, "org.dmfs.tasks", 1000),
                Entry(8, 2, "org.tasks.opentasks", 2000), // highest lastSync for collection 2

                Entry(9, 3, "org.tasks.opentasks", 1000),
            )
            syncstats.forEach { (id, collectionId, authority, lastSync) ->
                db.execSQL(
                    "INSERT INTO syncstats (id, collectionId, authority, lastSync) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(id, collectionId, authority, lastSync)
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

                // Expect one TASKS row per collection (collections 1, 2, 3)
                assertEquals(
                    listOf(
                        Entry(1, 1, "CONTACTS"),
                        Entry(2, 1, "EVENTS"),
                        Entry(5, 1, "TASKS"),   // highest lastSync TASK for collection 1 is JTX Board
                        Entry(8, 2, "TASKS"),   // highest lastSync TASK for collection 2
                        Entry(9, 3, "TASKS"),   // only TASK for collection 3
                    ), found
                )
            }
        }
    )

    @Test
    fun testMigration_RemovesOrphanSyncstats() = testMigration(
        prepare = { db ->
            // Insert a valid service and one valid collection
            db.execSQL(
                "INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)",
                arrayOf<Any?>(1, "test", 1)
            )
            db.execSQL(
                "INSERT INTO collection (id, serviceId, url, type, privWriteContent, privUnbind, forceReadOnly, sync) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(1, 1, "https://example.com/1", 1, 1, 1, 0, 1)
            )

            // Valid syncstats row (referencing existing collection)
            db.execSQL(
                "INSERT INTO syncstats (id, collectionId, authority, lastSync) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(1, 1, "com.android.contacts", 1234)
            )

            // Two orphan syncstats rows (collectionId not in collection)
            db.execSQL(
                "INSERT INTO syncstats (id, collectionId, authority, lastSync) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(2, 99, "com.android.calendar", 1660521600)
            )
            db.execSQL(
                "INSERT INTO syncstats (id, collectionId, authority, lastSync) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(3, 100, "org.dmfs.tasks", 1660521600)
            )
        },
        validate = { db ->
            // After migration, orphan rows should have been deleted
            db.query("SELECT id, collectionId, dataType FROM syncstats ORDER BY id").use { cursor ->
                val ids = mutableListOf<Int>()
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(0)
                    val collectionId = cursor.getLong(1)
                    assertTrue(collectionId == 1L) // only valid collectionId should remain
                    ids.add(id)
                }

                // Also verify that the orphaned IDs 2 and 3 were deleted
                assertTrue(2 !in ids && 3 !in ids)
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
