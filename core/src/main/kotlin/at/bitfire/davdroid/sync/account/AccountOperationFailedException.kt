/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

/**
 * An operation with accounts failed for any reason.
 */
open class AccountOperationFailedException(message: String? = null, cause: Throwable? = null): Exception(message, cause)
