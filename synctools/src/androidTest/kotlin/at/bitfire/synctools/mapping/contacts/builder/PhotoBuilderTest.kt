/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.Manifest
import android.net.Uri
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import kotlin.random.Random

class PhotoBuilderTest {

    companion object {
        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!
    }


    @Test
    fun testBuild_NoPhoto() {
        PhotoBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testBuild_Photo() {
        val blob = ByteArray(1024) { Random.nextInt().toByte() }
        PhotoBuilder(Uri.EMPTY, null, Contact().apply {
            photo = blob
        }, false).build().also { result ->
            // no row because photos have to be set with a separate call to AndroidAddressBook.setPhoto()
            assertEquals(0, result.size)
        }
    }

}
