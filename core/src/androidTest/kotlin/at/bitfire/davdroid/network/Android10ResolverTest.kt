/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.os.Build
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.Type
import java.net.Inet4Address
import java.net.InetAddress

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class Android10ResolverTest {

    val FQDN_DAVX5 = "www.davx5.com"

    @Test
    fun testResolveA() {
        val www = InetAddress.getAllByName(FQDN_DAVX5).filterIsInstance<Inet4Address>().first()

        val lookup = Lookup(FQDN_DAVX5, Type.A)
        lookup.setResolver(Android10Resolver())
        val resultGeneric = lookup.run()
        assertEquals(1, resultGeneric.size)

        val result = resultGeneric.first() as ARecord
        assertEquals(www, result.address)
    }

}