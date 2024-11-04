/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.workaround

import android.content.ContentValues
import android.os.Build
import android.os.RemoteException
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalContact.Companion.COLUMN_HASHCODE
import at.bitfire.vcard4android.BatchOperation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

class Android7DirtyVerifier @Inject constructor(
    val logger: Logger
): ContactDirtyVerifier {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("Android7DirtyVerifier must not be used on Android != 7.x")
    }


    // address-book level functions

    override fun prepareAddressBook(addressBook: LocalAddressBook, isUpload: Boolean): Boolean {
        val reallyDirty = verifyDirtyContacts(addressBook)
        val deleted = addressBook.findDeleted().size
        if (isUpload && reallyDirty == 0 && deleted == 0) {
            logger.info("This sync was called to up-sync dirty/deleted contacts, but no contacts have been changed")
            return false
        }

        // sync address book
        return true
    }

    /**
     * Queries all contacts with DIRTY flag and checks whether their data checksum has changed, i.e.
     * if they're "really dirty" (= data has changed, not only metadata, which is not hashed).
     * The DIRTY flag is removed from contacts which are not "really dirty", i.e. from contacts
     * whose contact data checksum has not changed.
     * @return number of "really dirty" contacts
     * @throws RemoteException on content provider errors
     */
    private fun verifyDirtyContacts(addressBook: LocalAddressBook): Int {
        var reallyDirty = 0
        for (contact in addressBook.findDirtyContacts()) {
            val lastHash = getLastHashCode(addressBook, contact)
            val currentHash = contactDataHashCode(contact)
            if (lastHash == currentHash) {
                // hash is code still the same, contact is not "really dirty" (only metadata been have changed)
                logger.log(Level.FINE, "Contact data hash has not changed, resetting dirty flag", contact)
                contact.resetDirty()
            } else {
                logger.log(Level.FINE, "Contact data has changed from hash $lastHash to $currentHash", contact)
                reallyDirty++
            }
        }

        if (addressBook.includeGroups)
            reallyDirty += addressBook.findDirtyGroups().size

        return reallyDirty
    }

    private fun getLastHashCode(addressBook: LocalAddressBook, contact: LocalContact): Int {
        addressBook.provider!!.query(contact.rawContactSyncURI(), arrayOf(COLUMN_HASHCODE), null, null, null)?.use { c ->
            if (c.moveToNext() && !c.isNull(0))
                return c.getInt(0)
        }
        return 0
    }


    // contact level functions

    /**
     * Calculates a hash code from the contact's data (VCard) and group memberships.
     * Attention: re-reads {@link #contact} from the database, discarding all changes in memory
     *
     * @return hash code of contact data (including group memberships)
     */
    private fun contactDataHashCode(contact: LocalContact): Int {
        contact.clearCachedContact()

        // groupMemberships is filled by getContact()
        val dataHash = contact.hashCode()
        val groupHash = contact.groupMemberships.hashCode()
        logger.finest("Calculated data hash = $dataHash, group memberships hash = $groupHash")
        return dataHash xor groupHash
    }

    override fun setHashCodeColumn(contact: LocalContact, values: ContentValues) {
        val hashCode = contactDataHashCode(contact)
        values.put(COLUMN_HASHCODE, hashCode)
    }

    override fun updateHashCode(addressBook: LocalAddressBook, contact: LocalContact) {
        val values = ContentValues(1)
        setHashCodeColumn(contact, values)

        addressBook.provider!!.update(contact.rawContactSyncURI(), values, null, null)
    }

    override fun updateHashCode(contact: LocalContact, batch: BatchOperation) {
        val hashCode = contactDataHashCode(contact)

        batch.enqueue(BatchOperation.CpoBuilder
            .newUpdate(contact.rawContactSyncURI())
            .withValue(COLUMN_HASHCODE, hashCode))
    }



    // factory

    @Module
    @InstallIn(SingletonComponent::class)
    object Android7DirtyVerifierModule {

        /**
         * Provides an [Android7DirtyVerifier] on Android 7.x, or an empty [Optional] on other versions.
         */
        @Provides
        fun provide(android7DirtyVerifier: Provider<Android7DirtyVerifier>): Optional<ContactDirtyVerifier> =
            if (/* Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && */ Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                Optional.of(android7DirtyVerifier.get())
            else
                Optional.empty()

    }

}