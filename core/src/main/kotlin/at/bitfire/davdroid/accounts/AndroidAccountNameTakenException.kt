/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

/**
 * Thrown when a DB account can't use a name because an orphaned Android account (for DB accounts) has this name.
 */
class AndroidAccountNameTakenException(name: String): Exception("Android account with name \"$name\" already exists")
