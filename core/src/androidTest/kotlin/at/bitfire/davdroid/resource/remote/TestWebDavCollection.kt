/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class TestWebDavCollection(httpClient: HttpClient, url: Url) : BaseWebDavCollection(httpClient, url) {

    override val davCollection = DavCollection(httpClient, url)

    var listFilteredMembersResult = emptyList<MemberState>()
    override fun listFilteredMembers(): Flow<MemberState> = listFilteredMembersResult.asFlow()

    override fun multiget(
        urls: List<Url>,
        capabilities: WebDavCollection.Capabilities
    ): Flow<WebDavCollection.MultiGetItem> =
        throw NotImplementedError()

}
