/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.accounts.Account

class InvalidAccountException(account: Account): Exception("Invalid account: $account")