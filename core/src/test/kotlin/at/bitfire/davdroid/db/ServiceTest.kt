/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import org.junit.Test
import kotlin.test.assertFailsWith

class ServiceTest {

    @Test
    fun `accountId and accountName not set`() {
        assertFailsWith<IllegalArgumentException> {
            Service(id = 0L, accountName = null, accountId = null, type = Service.TYPE_CALDAV)
        }
    }

    @Test
    fun `accountId and accountName set`() {
        assertFailsWith<IllegalArgumentException> {
            Service(id = 0L, accountName = "accountName", accountId = 0L, type = Service.TYPE_CALDAV)
        }
    }

    @Test
    fun `accountId or accountName set`() {
        Service(id = 0L, accountName = "accountName", accountId = null, type = Service.TYPE_CALDAV)
        Service(id = 0L, accountName = null, accountId = 0L, type = Service.TYPE_CALDAV)
    }

}
