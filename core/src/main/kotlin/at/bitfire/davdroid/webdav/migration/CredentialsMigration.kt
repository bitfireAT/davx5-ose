/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.migration

import android.content.SharedPreferences
import at.bitfire.davdroid.webdav.CredentialsStore

interface CredentialsMigration {

    /**
     * Migrates the stored WebDAV credentials from the old version to the new version which
     * is set in [CredentialsStore.CURRENT_VERSION].
     *
     * **The new (target) version number is registered in the Hilt module as [Int] key of the multi-binding of [CredentialsMigration].**
     *
     * Migrations should operate "low-level" on the preferences and must not depend on [CredentialsStore],
     * so that they don't have to change when [CredentialsStore] changes.
     *
     * If the migration throws, the version is not increased and the migration is retried the next time
     * the credentials are accessed. Migrations must therefore be idempotent.
     *
     * @param prefs preferences that contain the credentials in the current storage (one entry per mount ID, key
     * format see [CredentialsStore.key])
     */
    fun migrate(prefs: SharedPreferences)

}
