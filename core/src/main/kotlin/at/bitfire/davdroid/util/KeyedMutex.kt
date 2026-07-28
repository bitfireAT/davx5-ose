/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * A [Mutex] per key: concurrent operations for different keys don't block each other, while
 * operations for the *same* key are still serialized. An entry is dropped again as soon as no
 * coroutine is holding or waiting for it, so the map doesn't grow unbounded over its lifetime.
 */
class KeyedMutex {

    class Entry {
        val mutex = Mutex()
        var refCount = 0
    }

    // access to a given key's Entry (including refCount) is only ever mutated from within
    // ConcurrentHashMap's atomic compute/computeIfPresent, so no additional locking is needed here
    val locks = ConcurrentHashMap<String, Entry>()

    suspend inline fun <T> withLock(key: String, action: () -> T): T {
        val entry = locks.compute(key) { _, existing ->
            (existing ?: Entry()).apply { refCount++ }
        }!!
        try {
            return entry.mutex.withLock {
                action()
            }
        } finally {
            locks.computeIfPresent(key) { _, e ->
                e.refCount--
                if (e.refCount <= 0) null else e
            }
        }
    }

}
