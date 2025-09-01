/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.ProvidedAutoMigrationSpec
import androidx.room.migration.AutoMigrationSpec
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.davdroid.settings.Credentials
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * Moves WebDAV credentials from the deprecated EncryptedSharedPreferences to the database.
 */
@ProvidedAutoMigrationSpec
class AutoMigration19 @Inject constructor(
    @ApplicationContext context: Context
) : AutoMigrationSpec {

    @Suppress("DEPRECATION")
    private val legacyMasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    @Suppress("DEPRECATION")
    private val legacyPrefs = EncryptedSharedPreferences.create(context, "webdav_credentials", legacyMasterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)


    private fun keyName(mountId: Long, name: String) =
        "$mountId.$name"

    private fun legacyCredentials(mountId: Long): Credentials? {
        if (!legacyPrefs.getBoolean(keyName(mountId, LEGACY_HAS_CREDENTIALS), false))
            return null

        return Credentials(
            legacyPrefs.getString(keyName(mountId, LEGACY_USER_NAME), null),
            legacyPrefs.getString(keyName(mountId, LEGACY_PASSWORD), null)?.toCharArray(),
            legacyPrefs.getString(keyName(mountId, LEGACY_CERTIFICATE_ALIAS), null)
        )
    }

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        // Iterate all WebDAV mounts and move credentials (if any).
        db.query("SELECT id FROM webdav_mounts").use { cursor ->
            val mountId = cursor.getLong(0)

            // get legacy credentials
            val credentials = legacyCredentials(mountId)
            if (credentials != null) {
                val credentialsFields = contentValuesOf(
                    "username" to credentials.username,
                    "password" to credentials.password?.toString(),
                    "certificateAlias" to credentials.certificateAlias
                )
                db.update(
                    "webdav_mounts", SQLiteDatabase.CONFLICT_IGNORE, credentialsFields,
                    "id=?", arrayOf(mountId.toString())
                )
            }
        }
    }

    companion object {
        private const val LEGACY_HAS_CREDENTIALS = "has_credentials"
        private const val LEGACY_USER_NAME = "user_name"
        private const val LEGACY_PASSWORD = "password"
        private const val LEGACY_CERTIFICATE_ALIAS = "certificate_alias"
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AutoMigrationModule {
        @Binds
        @IntoSet
        abstract fun provide(impl: AutoMigration19): AutoMigrationSpec
    }

}