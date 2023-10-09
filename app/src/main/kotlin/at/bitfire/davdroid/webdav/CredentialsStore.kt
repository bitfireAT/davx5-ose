/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import android.content.Context
import androidx.annotation.StringDef
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import at.bitfire.davdroid.db.Credentials

class CredentialsStore(context: Context) {

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
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(context, "webdav_credentials", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)


    fun getCredentials(mountId: Long): Credentials? {
        if (!prefs.getBoolean(keyName(mountId, HAS_CREDENTIALS), false))
            return null

        return Credentials(
            prefs.getString(keyName(mountId, USER_NAME), null),
            prefs.getString(keyName(mountId, PASSWORD), null),
            prefs.getString(keyName(mountId, CERTIFICATE_ALIAS), null)
        )
    }

    fun setCredentials(mountId: Long, credentials: Credentials?) {
        val edit = prefs.edit()
        if (credentials != null)
            edit.putBoolean(keyName(mountId, HAS_CREDENTIALS), true)
                .putString(keyName(mountId, USER_NAME), credentials.userName)
                .putString(keyName(mountId, PASSWORD), credentials.password)
                .putString(keyName(mountId, CERTIFICATE_ALIAS), credentials.certificateAlias)
        else
            edit.remove(keyName(mountId, HAS_CREDENTIALS))
                .remove(keyName(mountId, USER_NAME))
                .remove(keyName(mountId, PASSWORD))
                .remove(keyName(mountId, CERTIFICATE_ALIAS))
        edit.apply()
    }


    private fun keyName(mountId: Long, @KeyName name: String) =
        "$mountId.$name"

}