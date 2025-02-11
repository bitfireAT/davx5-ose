/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.security.KeyChain
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Socket
import java.security.Principal
import javax.net.ssl.X509ExtendedKeyManager

/**
 * KeyManager that provides a client certificate and private key from the Android KeyChain.
 *
 * @throws IllegalArgumentException if the alias doesn't exist or is not accessible
 */
class ClientCertKeyManager @AssistedInject constructor(
    @Assisted private val alias: String,
    @ApplicationContext private val context: Context
): X509ExtendedKeyManager() {

    @AssistedFactory
    interface Factory {
        fun create(alias: String): ClientCertKeyManager
    }

    val certs = KeyChain.getCertificateChain(context, alias) ?: throw IllegalArgumentException("Alias doesn't exist or not accessible: $alias")
    val key = KeyChain.getPrivateKey(context, alias) ?: throw IllegalArgumentException("Alias doesn't exist or not accessible: $alias")

    override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?) = null

    override fun getClientAliases(p0: String?, p1: Array<out Principal>?) = arrayOf(alias)
    override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?) = alias

    override fun getCertificateChain(forAlias: String?) =
        certs.takeIf { forAlias == alias }

    override fun getPrivateKey(forAlias: String?) =
        key.takeIf { forAlias == alias }

}