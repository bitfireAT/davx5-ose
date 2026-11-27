/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.security

import android.content.Context
import at.bitfire.davdroid.BuildConfig
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.inject.Singleton

@Singleton
class CredentialCipher(@ApplicationContext context: Context) {

    private val aead: Aead by lazy {
        AeadConfig.register()
        val handle: KeysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        handle.getPrimitive(Aead::class.java)
    }

    fun encryptSecret(plainSecret: String, id: String): ByteArray {
        val plaintextBytes = plainSecret.toByteArray(StandardCharsets.UTF_8)
        return try {
            aead.encrypt(plaintextBytes, associatedData(id))
        } finally {
            plaintextBytes.fill(0)
        }
    }

    /**
     * Throws GeneralSecurityException if tampered, wrong id, or unrecoverable on a restored device.
     */
    @Throws(GeneralSecurityException::class)
    fun decryptSecret(ciphertext: ByteArray, id: String): String {
        val decryptedBytes = aead.decrypt(ciphertext, associatedData(id))
        return try {
            String(decryptedBytes, StandardCharsets.UTF_8)
        } finally {
            decryptedBytes.fill(0)
        }
    }

    private fun associatedData(id: String): ByteArray =
        "credential.secret:$id".toByteArray(StandardCharsets.UTF_8)

    companion object {
        private val VARIANT_PREFIX = BuildConfig.LIBRARY_PACKAGE_NAME.substringAfterLast('.')
        private val KEYSET_NAME = "${VARIANT_PREFIX}_credentials_keyset"
        private const val PREF_FILE_NAME = "tink_credential_prefs"
        private val MASTER_KEY_URI = "android-keystore://${VARIANT_PREFIX}_credentials_master_key"
    }
}
