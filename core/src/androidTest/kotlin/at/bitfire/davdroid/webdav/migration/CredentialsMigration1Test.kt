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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
class CredentialsMigration1Test {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var migration: CredentialsMigration1

    @Inject
    lateinit var serializerFactory: CredentialsSerializer.Factory

    private lateinit var newPrefs: SharedPreferences

    @Before
    fun setUp() {
        hiltRule.inject()
        context.deleteSharedPreferences(LEGACY_PREF_FILE_NAME)
        context.deleteSharedPreferences(NEW_PREF_FILE_NAME)
        newPrefs = context.getSharedPreferences(NEW_PREF_FILE_NAME, Context.MODE_PRIVATE)
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences(LEGACY_PREF_FILE_NAME)
        context.deleteSharedPreferences(NEW_PREF_FILE_NAME)
    }


    private fun legacyPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(context, LEGACY_PREF_FILE_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    private fun writeLegacy(mountId: Long, username: String?, password: String?, certificateAlias: String?) {
        legacyPrefs().edit(commit = true) {
            putBoolean("$mountId.has_credentials", true)
            putString("$mountId.user_name", username)
            putString("$mountId.password", password)
            putString("$mountId.certificate_alias", certificateAlias)
        }
    }

    private fun readMigrated(mountId: Long): Credentials? =
        newPrefs.getString(CredentialsStore.key(mountId), null)?.let { json ->
            Json.decodeFromString(serializerFactory.create(mountId), json)
        }

    private fun legacyFileExists() =
        File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREF_FILE_NAME.xml").exists()


    @Test
    fun testMigrate_NoLegacyPrefs() {
        migration.migrate(newPrefs)

        assertTrue(newPrefs.all.isEmpty())
        assertFalse(legacyFileExists())
    }

    @Test
    fun testMigrate_BasicCredentials() {
        writeLegacy(1, "user", "secret", null)

        migration.migrate(newPrefs)

        assertEquals(Credentials(username = "user", password = "secret".toSensitiveString()), readMigrated(1))
    }

    @Test
    fun testMigrate_CertificateAlias() {
        writeLegacy(2, null, null, "my-alias")

        migration.migrate(newPrefs)

        assertEquals(Credentials(certificateAlias = "my-alias"), readMigrated(2))
    }

    @Test
    fun testMigrate_MultipleMounts() {
        writeLegacy(1, "user1", "pass1", null)
        writeLegacy(23, "user23", "pass23", null)

        migration.migrate(newPrefs)

        assertEquals(Credentials(username = "user1", password = "pass1".toSensitiveString()), readMigrated(1))
        assertEquals(Credentials(username = "user23", password = "pass23".toSensitiveString()), readMigrated(23))
    }

    @Test
    fun testMigrate_MountWithoutCredentials_IsSkipped() {
        writeLegacy(1, "user", "secret", null)
        legacyPrefs().edit(commit = true) {
            putBoolean("2.has_credentials", false)
            putString("2.user_name", "stale")
        }

        migration.migrate(newPrefs)

        assertNotNull(readMigrated(1))
        assertNull(readMigrated(2))
    }

    @Test
    fun testMigrate_PasswordIsEncryptedAndBoundToMountId() {
        writeLegacy(1, "user", "secret", null)

        migration.migrate(newPrefs)

        val stored = newPrefs.getString(CredentialsStore.key(1), null)!!
        assertFalse(stored.contains("secret"))

        // ciphertext of mount 1 must not decrypt when read as another mount
        val result = runCatching { Json.decodeFromString(serializerFactory.create(2), stored) }
        assertTrue(result.isFailure)
    }

    @Test
    fun testMigrate_ExistingNewEntry_IsNotOverwritten() {
        writeLegacy(1, "old-user", "old-pass", null)
        val existing = Credentials(username = "new-user", password = "new-pass".toSensitiveString())
        newPrefs.edit(commit = true) {
            putString(CredentialsStore.key(1), Json.encodeToString(serializerFactory.create(1), existing))
        }

        migration.migrate(newPrefs)

        assertEquals(existing, readMigrated(1))
    }

    @Test
    fun testMigrate_DeletesLegacyPrefs() {
        writeLegacy(1, "user", "secret", null)
        assertTrue(legacyFileExists())

        migration.migrate(newPrefs)

        assertFalse(legacyFileExists())
    }

    @Test
    fun testMigrate_RunTwice_IsIdempotent() {
        writeLegacy(1, "user", "secret", null)

        migration.migrate(newPrefs)
        migration.migrate(newPrefs)     // legacy prefs are gone now → no-op

        assertEquals(Credentials(username = "user", password = "secret".toSensitiveString()), readMigrated(1))
    }


    companion object {
        private const val LEGACY_PREF_FILE_NAME = "webdav_credentials"
        private const val NEW_PREF_FILE_NAME = "webdav_credentials_migration_test"
    }

}
