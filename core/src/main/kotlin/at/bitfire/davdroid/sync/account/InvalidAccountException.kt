/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import at.bitfire.davdroid.accounts.AccountId

/**
 * Thrown when an account is invalid (usually because it doesn't exist anymore).
 */
class InvalidAccountException(accountId: AccountId): Exception("Invalid account: $accountId")