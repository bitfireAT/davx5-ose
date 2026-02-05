/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.security.KeyChain
import android.security.KeyChainException
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ssl.X509ExtendedKeyManager

/**
 * KeyManager that provides a client certificate and private key from the Android [KeyChain].
 *
 * Requests for certificates / private keys for other aliases than the specified one
 * will be ignored.
 *
 * @param alias     alias of the desired certificate / private key
 */
class ClientCertKeyManager @AssistedInject constructor(
    @Assisted private val alias: String,
    @ApplicationContext private val context: Context,
    private val logger: Logger
): X509ExtendedKeyManager() {

    @AssistedFactory
    interface Factory {
        fun create(alias: String): ClientCertKeyManager
    }

    override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?) = null

    override fun getClientAliases(p0: String?, p1: Array<out Principal>?) = arrayOf(alias)
    override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?) = alias

    override fun getCertificateChain(forAlias: String): Array<X509Certificate>? {
        if (forAlias != alias)
            return null

        return try {
            KeyChain.getCertificateChain(context, alias).also { result ->
                if (result == null)
                    logger.warning("Couldn't obtain certificate chain for alias $alias")
            }
        } catch (e: KeyChainException) {
            // Android <Q throws an exception instead of returning null
            logger.log(Level.WARNING, "Couldn't obtain certificate chain for alias $alias", e)
            null
        }
    }

    override fun getPrivateKey(forAlias: String): PrivateKey? {
        if (forAlias != alias)
            return null

        return try {
            KeyChain.getPrivateKey(context, alias).also { result ->
                if (result == null)
                    logger.log(Level.WARNING, "Couldn't obtain private key for alias $alias")
            }
        } catch (e: KeyChainException) {
            // Android <Q throws an exception instead of returning null
            logger.log(Level.WARNING, "Couldn't obtain private key for alias $alias", e)
            null
        }
    }

}