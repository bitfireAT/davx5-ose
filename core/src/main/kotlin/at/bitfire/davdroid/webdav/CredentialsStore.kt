/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import at.bitfire.davdroid.security.CredentialsSerializer
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.webdav.migration.CredentialsMigration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

/**
 * Stores the [Credentials] of WebDAV mounts, keyed by mount ID.
 *
 * Credentials are serialized with [CredentialsSerializer] (the password is encrypted with Tink and bound to the mount ID).
 * The storage format is versioned; older storages are upgraded by [CredentialsMigration]s when the store is accessed
 * for the first time.
 */
class CredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val migrations: Map<Int, @JvmSuppressWildcards Provider<CredentialsMigration>>,
    private val serializerFactory: CredentialsSerializer.Factory
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE).also(::update)
    }

    /**
     * @return the stored credentials, or `null` if there are none for the given mount
     *
     * @throws CredentialsUnreadableException if credentials are stored, but can't be read (decryption failed / corrupt data)
     */
    fun getCredentials(mountId: Long): Credentials? {
        val json = prefs.getString(key(mountId), null) ?: return null
        return try {
            Json.decodeFromString(serializerFactory.create(mountId), json)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't read credentials of WebDAV mount $mountId", e)
            throw CredentialsUnreadableException(mountId, e)
        }
    }

    /**
     * Stores (or, when [credentials] is `null`, removes) the credentials of a mount.
     *
     * @throws Exception if the credentials can't be encrypted / stored (for instance when the Android Keystore is not usable)
     */
    fun setCredentials(mountId: Long, credentials: Credentials?) {
        prefs.edit {
            if (credentials != null)
                putString(key(mountId), Json.encodeToString(serializerFactory.create(mountId), credentials))
            else
                remove(key(mountId))
        }
    }


    // update from previous storage versions

    private fun update(prefs: SharedPreferences) {
        // synchronize because migrations must only be run one time
        synchronized(updateLock) {
            val baseVersion = prefs.getInt(KEY_VERSION, 0)
            for (toVersion in baseVersion + 1 .. CURRENT_VERSION) {
                val fromVersion = toVersion - 1
                logger.info("Updating WebDAV credentials storage version $fromVersion → $toVersion")

                val migration = migrations[toVersion]
                if (migration == null) {
                    logger.severe("No CredentialsStore migration $fromVersion → $toVersion")
                    return
                }

                try {
                    migration.get().migrate(prefs)
                    prefs.edit(commit = true) { putInt(KEY_VERSION, toVersion) }
                    logger.info("WebDAV credentials storage update to $toVersion successful")
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Couldn't run CredentialsStore migration $fromVersion → $toVersion", e)
                    return
                }
            }
        }
    }


    companion object {

        /**
         * Current (usually the newest) storage version. It's used to determine whether
         * a migration ([CredentialsMigration]) should be performed.
         */
        const val CURRENT_VERSION = 1

        private const val PREF_FILE_NAME = "webdav_credentials_store"

        /** Key of the storage version. Doesn't clash with mount keys, which are numeric. */
        private const val KEY_VERSION = "version"

        private val updateLock = Any()

        /** Preferences key of the credentials of the given mount. */
        fun key(mountId: Long) = mountId.toString()

    }

}
