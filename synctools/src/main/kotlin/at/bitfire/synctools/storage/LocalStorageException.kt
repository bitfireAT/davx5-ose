/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

/**
 * Indicates a problem with a local storage operation, such as failing to insert or update a row
 * in contacts or calendar storage.
 *
 * @param message A detail message explaining the cause of the error.
 * @param cause The throwable that caused this exception, if any.
 */
class LocalStorageException @JvmOverloads constructor(
    message: String?,
    cause: Throwable? = null
) : Exception(message, cause)
