/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import com.google.common.base.Throwables

/**
 * Searches this [Throwable] and its whole cause chain (redundantly, i.e. checking every level,
 * not just the immediate cause) for the first [Throwable] of type [T].
 *
 * @return the first matching [Throwable] in the chain (which may be this [Throwable] itself), or `null` if none matches
 */
inline fun <reified T : Throwable> Throwable.causedBy(): T? =
    Throwables.getCausalChain(this).filterIsInstance<T>().firstOrNull()
