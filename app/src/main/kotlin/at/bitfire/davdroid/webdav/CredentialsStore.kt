/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.StringDef
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.bitfire.davdroid.db.Credentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject
import kotlin.reflect.KClass

class CredentialsStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "credentials")
    private val dataStore by lazy { context.dataStore }

    val ks: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    /*
     * Generate a new EC key pair entry in the Android Keystore by
     * using the KeyPairGenerator API. The private key can only be
     * used for signing or verification and only with SHA-256 or
     * SHA-512 as the message digest.
     */
    private fun generateSecretKey(alias: String): SecretKey {
        val keyEntry = ks.getEntry(alias, null)
        if (keyEntry == null) {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val parameterSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).run {
                setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                build()
            }
            kg.init(parameterSpec)

            ks.setKeyEntry(alias, kg.generateKey(), null, null)
        }

        return getSecretKey(alias) ?: error("There was an error while generating the key")
    }

    private fun getSecretKey(alias: String): SecretKey? {
        return ks.getKey(alias, null) as SecretKey?
    }

    private fun encrypt(key: Key, data: String): Pair<ByteArray, ByteArray> {
        return Cipher.getInstance(AES_MODE).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }.let { it.doFinal(data.encodeToByteArray()) to it.iv }
    }

    private fun decrypt(key: Key, encryptedData: ByteArray, iv: ByteArray): String {
        return Cipher.getInstance(AES_MODE).apply {
            init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        }.doFinal(encryptedData).decodeToString()
    }

    suspend fun getCredentials(mountId: Long): Credentials? {
        val hasCredentialsKey = preferenceKey<Boolean>(mountId, HAS_CREDENTIALS)
        val userNameKey = preferenceKey<String>(mountId, USER_NAME)
        val passwordKey = preferenceKey<String>(mountId, PASSWORD)
        val cerAliasKey = preferenceKey<String>(mountId, CERTIFICATE_ALIAS)

        val data = dataStore.data.first()
        if (data[hasCredentialsKey] != true) return null

        val key = getSecretKey(mountId.toString())
        checkNotNull(key) { "Could not find any key for mount $mountId" }

        val username = data.getWithIV(userNameKey)?.let { (value, iv) -> decrypt(key, value.encodeToByteArray(), iv) }
        val password = data.getWithIV(passwordKey)?.let { (value, iv) -> decrypt(key, value.encodeToByteArray(), iv) }
        val cerAlias = data.getWithIV(cerAliasKey)?.let { (value, iv) -> decrypt(key, value.encodeToByteArray(), iv) }

        return Credentials(username, password, cerAlias)
    }

    suspend fun setCredentials(mountId: Long, credentials: Credentials?) {
        val alias = mountId.toString()
        val key = getSecretKey(alias) ?: generateSecretKey(alias)

        dataStore.edit { pref ->
            val hasCredentials = preferenceKey<Boolean>(mountId, HAS_CREDENTIALS)
            val userNameKey = preferenceKey<String>(mountId, USER_NAME)
            val passwordKey = preferenceKey<String>(mountId, PASSWORD)
            val cerAliasKey = preferenceKey<String>(mountId, CERTIFICATE_ALIAS)

            if (credentials == null) {
                pref.remove(hasCredentials)
                pref.remove(userNameKey)
                pref.remove(userNameKey.iv())
                pref.remove(passwordKey)
                pref.remove(passwordKey.iv())
                pref.remove(cerAliasKey)
                pref.remove(cerAliasKey.iv())
            } else {
                check(credentials.username != null || credentials.password != null || credentials.certificateAlias != null) {
                    "Credentials given are all-null"
                }

                credentials.username?.let { username ->
                    val (data, iv) = encrypt(key, username)
                    pref.setWithIV(userNameKey, data.decodeToString(), iv)
                }
                credentials.password?.let { password ->
                    val (data, iv) = encrypt(key, password)
                    pref.setWithIV(passwordKey, data.decodeToString(), iv)
                }
                credentials.certificateAlias?.let { certificateAlias ->
                    val (data, iv) = encrypt(key, certificateAlias)
                    pref.setWithIV(cerAliasKey, data.decodeToString(), iv)
                }

                pref[hasCredentials] = true
            }
        }
    }

    private fun <Type : Any> preferenceKeyFromGeneric(keyName: String, type: KClass<Type>): Preferences.Key<Type> {
        val key = when (type) {
            String::class -> stringPreferencesKey(keyName)
            Boolean::class -> booleanPreferencesKey(keyName)
            else -> error("Got unsupported type ${type.simpleName}")
        }
        @Suppress("UNCHECKED_CAST")
        return key as Preferences.Key<Type>
    }

    private fun <Type : Any> preferenceKey(
        mountId: Long,
        @KeyName name: String,
        suffix: String = "",
        type: KClass<Type>,
    ): Preferences.Key<Type> {
        val keyName = "$mountId.$name$suffix"
        return preferenceKeyFromGeneric(keyName, type)
    }

    private inline fun <reified Type : Any> preferenceKey(
        mountId: Long,
        @KeyName name: String,
        suffix: String = "",
    ): Preferences.Key<Type> = preferenceKey(mountId, name, suffix, Type::class)

    private fun <Type : Any> Preferences.Key<Type>.iv(): Preferences.Key<String> {
        return preferenceKeyFromGeneric("$name.iv", String::class)
    }

    private inline fun <reified Type : Any> MutablePreferences.setWithIV(key: Preferences.Key<Type>, value: Type, iv: ByteArray) {
        set(key, value)
        set(key.iv(), iv.decodeToString())
    }

    private inline fun <reified Type : Any> Preferences.getWithIV(key: Preferences.Key<Type>): Pair<Type, ByteArray>? {
        val value = get(key)
        val iv = get(key.iv())?.encodeToByteArray()
        return if (value != null && iv != null) value to iv
        else null
    }


    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        HAS_CREDENTIALS,
        USER_NAME,
        PASSWORD,
        CERTIFICATE_ALIAS
    )
    annotation class KeyName

    companion object {
        const val HAS_CREDENTIALS = "has_credentials"
        const val USER_NAME = "user_name"
        const val PASSWORD = "password"
        const val CERTIFICATE_ALIAS = "certificate_alias"

        const val KEYSTORE_PROVIDER = "AndroidKeyStore"

        const val AES_MODE = KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7
    }

}
