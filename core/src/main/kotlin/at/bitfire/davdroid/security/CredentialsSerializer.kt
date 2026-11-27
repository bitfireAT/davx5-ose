/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.security

import at.bitfire.davdroid.settings.Credentials
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import net.openid.appauth.AuthState
import kotlin.io.encoding.Base64

/**
 * Serializes [Credentials]. The password and the OAuth auth state are stored encrypted (Base64 of the ciphertext) using [CredentialCipher],
 * bound to [mountId] as associated data. Create instances with [Factory].
 */
class CredentialsSerializer @AssistedInject constructor(
    @Assisted private val mountId: Long,
    private val cipher: CredentialCipher
) : KSerializer<Credentials> {

    @AssistedFactory
    interface Factory {
        fun create(mountId: Long): CredentialsSerializer
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("at.bitfire.davdroid.settings.CredentialsSerializer") {
        element<String?>("username", isOptional = true)
        element<String?>("password", isOptional = true)
        element<String?>("certificateAlias", isOptional = true)
        element<String?>("authState", isOptional = true)
    }

    companion object {
        const val IDX_USERNAME = 0
        const val IDX_PASSWORD = 1
        const val IDX_CERT_ALIAS = 2
        const val IDX_AUTH_STATE = 3
    }

    override fun serialize(
        encoder: Encoder,
        value: Credentials
    ) {
        encoder.encodeStructure(descriptor) {
            value.username?.let { username ->
                encodeStringElement(descriptor, IDX_USERNAME, username)
            }
            value.password?.let { password ->
                val bytes = cipher.encryptSecret(password.asString(), mountId.toString())
                val base64 = Base64.Mime.encode(bytes)
                encodeStringElement(descriptor, IDX_PASSWORD, base64)
            }
            value.certificateAlias?.let { alias ->
                encodeStringElement(descriptor, IDX_CERT_ALIAS, alias)
            }
            value.authState?.jsonSerializeString()?.let { json ->
                // contains refresh and access tokens, so it must be encrypted as well
                val bytes = cipher.encryptSecret(json, authStateId())
                encodeStringElement(descriptor, IDX_AUTH_STATE, Base64.Mime.encode(bytes))
            }
        }
    }

    override fun deserialize(decoder: Decoder): Credentials =
        decoder.decodeStructure(descriptor) {
            var username: String? = null
            var passwordBase64: String? = null
            var certificateAlias: String? = null
            var authStateBase64: String? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    IDX_USERNAME -> username = decodeStringElement(descriptor, IDX_USERNAME)
                    IDX_PASSWORD -> passwordBase64 = decodeStringElement(descriptor, IDX_PASSWORD)
                    IDX_CERT_ALIAS -> certificateAlias = decodeStringElement(descriptor, IDX_CERT_ALIAS)
                    IDX_AUTH_STATE -> authStateBase64 = decodeStringElement(descriptor, IDX_AUTH_STATE)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index $index")
                }
            }

            val password = passwordBase64?.let { base64 ->
                val bytes = Base64.Mime.decode(base64)
                cipher.decryptSecret(bytes, mountId.toString())
            }?.toSensitiveString()

            val authState = authStateBase64?.let { base64 ->
                val bytes = Base64.Mime.decode(base64)
                AuthState.jsonDeserialize(cipher.decryptSecret(bytes, authStateId()))
            }

            Credentials(username, password, certificateAlias, authState)
        }

    /** Associated data ID of the auth state, so that it can't be swapped with the password ciphertext. */
    private fun authStateId() = "$mountId.authState"
}
