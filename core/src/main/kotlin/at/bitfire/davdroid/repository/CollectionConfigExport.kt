/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CollectionConfigExport(
    val version: Int = 1,
    val accountName: String,
    val baseUrl: String? = null,
    val credentials: AccountCredentials? = null,
    val collections: List<CollectionConfig>
)

@Serializable
data class AccountCredentials(
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class CollectionConfig(
    val url: String,
    val type: String,
    val sync: Boolean,
    val forceReadOnly: Boolean
)

object CollectionConfigSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun export(
        accountName: String,
        collections: List<Collection>,
        credentials: AccountCredentials? = null,
        baseUrl: String? = null
    ): String {
        val config = CollectionConfigExport(
            accountName = accountName,
            baseUrl = baseUrl,
            credentials = credentials,
            collections = collections.map { collection ->
                CollectionConfig(
                    url = collection.url.toString(),
                    type = collection.type,
                    sync = collection.sync,
                    forceReadOnly = collection.forceReadOnly
                )
            }
        )
        return json.encodeToString(config)
    }

    fun parse(jsonString: String): CollectionConfigExport =
        json.decodeFromString(jsonString)

}
