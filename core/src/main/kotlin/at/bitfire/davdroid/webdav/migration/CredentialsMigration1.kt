/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import at.bitfire.davdroid.security.CredentialsSerializer
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.webdav.CredentialsStore
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.json.Json
import java.io.File
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Credentials used to be stored as separate entries in [EncryptedSharedPreferences] (deprecated by Jetpack).
 *
 * This migration serializes them with [CredentialsSerializer] (password encrypted with Tink and bound to the mount ID)
 * and deletes the legacy preferences afterwards.
 */
class CredentialsMigration1 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val serializerFactory: CredentialsSerializer.Factory
): CredentialsMigration {

    override fun migrate(prefs: SharedPreferences) {
        // don't create legacy preferences (and a master key) if there's nothing to migrate
        val legacyFile = File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREF_FILE_NAME.xml")
        if (!legacyFile.exists())
            return

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val legacy = EncryptedSharedPreferences.create(context, LEGACY_PREF_FILE_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

        // legacy keys have the format "<mountId>.<name>"
        val mountIds = legacy.all.keys.mapNotNull { it.substringBefore('.').toLongOrNull() }.toSet()
        for (mountId in mountIds) {
            if (!legacy.getBoolean("$mountId.$HAS_CREDENTIALS", false))
                continue

            val key = CredentialsStore.key(mountId)
            if (prefs.contains(key))
                continue    // already migrated

            val credentials = Credentials(
                legacy.getString("$mountId.$USER_NAME", null),
                legacy.getString("$mountId.$PASSWORD", null)?.toSensitiveString(),
                legacy.getString("$mountId.$CERTIFICATE_ALIAS", null)
            )
            val json = Json.encodeToString(serializerFactory.create(mountId), credentials)
            prefs.edit(commit = true) { putString(key, json) }
            logger.fine("Migrated credentials of WebDAV mount $mountId")
        }

        // all credentials have been migrated
        context.deleteSharedPreferences(LEGACY_PREF_FILE_NAME)
    }


    companion object {
        private const val LEGACY_PREF_FILE_NAME = "webdav_credentials"
        private const val HAS_CREDENTIALS = "has_credentials"
        private const val USER_NAME = "user_name"
        private const val PASSWORD = "password"
        private const val CERTIFICATE_ALIAS = "certificate_alias"
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class CredentialsMigrationModule {
        @Binds @IntoMap
        @IntKey(1)
        abstract fun provide(impl: CredentialsMigration1): CredentialsMigration
    }

}
