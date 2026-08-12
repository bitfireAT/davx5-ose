/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.ContentProviderClient
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalAddress
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalGroup
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.remote.CardDavCollection
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.resource.workaround.ContactDirtyVerifier
import at.bitfire.davdroid.sync.groups.CategoriesStrategy
import at.bitfire.davdroid.sync.groups.VCard4Strategy
import at.bitfire.davdroid.sync.mapping.ContactMapper
import at.bitfire.davdroid.sync.mapping.ResourceMapper
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.ContactReader
import at.bitfire.synctools.vcard.GroupMethod
import at.bitfire.synctools.vcard.VCardParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import java.io.Reader
import java.io.StringReader
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * Synchronization manager for CardDAV collections; handles contacts and groups.
 *
 * Group handling differs according to the {@link #groupMethod}. There are two basic methods to
 * handle/manage groups:
 *
 * 1. CATEGORIES: groups memberships are attached to each contact and represented as
 *   "category". When a group is dirty or has been deleted, all its members have to be set to
 *   dirty, too (because they have to be uploaded without the respective category). This
 *   is done in [uploadDirty]. Empty groups can be deleted without further processing,
 *   which is done in [postProcess] because groups may become empty after downloading
 *   updated remote contacts.
 *
 * 2. Groups as separate VCards: individual and group contacts (with a list of member UIDs) are
 *   distinguished. When a local group is dirty, its members don't need to be set to dirty.
 *
 *   However, when a contact is dirty, it has
 *   to be checked whether its group memberships have changed. In this case, the respective
 *   groups have to be set to dirty. For instance, if contact A is in group G and H, and then
 *   group membership of G is removed, the contact will be set to dirty because of the changed
 *   [android.provider.ContactsContract.CommonDataKinds.GroupMembership]. DAVx5 will
 *   then have to check whether the group memberships have actually changed, and if so,
 *   all affected groups have to be set to dirty. To detect changes in group memberships,
 *   DAVx5 always mirrors all [android.provider.ContactsContract.CommonDataKinds.GroupMembership]
 *   data rows in respective [at.bitfire.vcard4android.CachedGroupMembership] rows.
 *   If the cached group memberships are not the same as the current group member ships, the
 *   difference set (in our example G, because its in the cached memberships, but not in the
 *   actual ones) is marked as dirty. This is done in [uploadDirty].
 *
 *   When downloading remote contacts, groups (+ member information) may be received
 *   by the actual members. Thus, the member lists have to be cached until all VCards
 *   are received. This is done by caching the member UIDs of each group in
 *   [AddressContract.GroupColumns.PENDING_MEMBERS]. In [postProcess],
 *   these "pending memberships" are assigned to the actual contacts and then cleaned up.
 *
 * @param syncFrameworkUpload   set when this sync is caused by the sync framework and [android.content.ContentResolver.SYNC_EXTRAS_UPLOAD] was set
 */
class ContactsSyncManager @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted val provider: ContentProviderClient,
    @Assisted override val localCollection: LocalAddressBook,
    @Assisted collectionInfo: Collection,
    @Assisted override val remoteCollection: CardDavCollection,
    @Assisted resync: ResyncType?,
    @Assisted val syncFrameworkUpload: Boolean,
    @Assisted settings: SyncSettings,
    contactMapper: ContactMapper,
    val dirtyVerifier: Optional<ContactDirtyVerifier>,
    private val resourceRetrieverFactory: ResourceRetriever.Factory
) : SyncManager<LocalAddress>(
    accountId,
    httpClient,
    SyncDataType.CONTACTS,
    syncResult,
    collectionInfo,
    resync,
    settings
) {

    @AssistedFactory
    interface Factory {
        fun contactsSyncManager(
            accountId: AccountId,
            httpClient: HttpClient,
            syncResult: SyncResult,
            provider: ContentProviderClient,
            localAddressBook: LocalAddressBook,
            collectionInfo: Collection,
            remoteCollection: CardDavCollection,
            resync: ResyncType?,
            syncFrameworkUpload: Boolean,
            settings: SyncSettings
        ): ContactsSyncManager
    }

    override val resourceMapper: ResourceMapper<LocalAddress> = contactMapper

    companion object {
        infix fun <T> Set<T>.disjunct(other: Set<T>) = (this - other) union (other - this)
    }

    private val groupStrategy = when (settings.groupMethod) {
        GroupMethod.GROUP_VCARDS -> VCard4Strategy(localCollection)
        GroupMethod.CATEGORIES -> CategoriesStrategy(localCollection)
    }


    override suspend fun prepare(): Boolean {
        if (dirtyVerifier.isPresent) {
            logger.info("Sync will verify dirty contacts (Android 7.x workaround)")
            if (!dirtyVerifier.get().prepareAddressBook(localCollection, isUpload = syncFrameworkUpload))
                return false
        }

        logger.info("Contact group strategy: ${groupStrategy::class.java.simpleName}")
        return true
    }

    override fun syncAlgorithm(capabilities: WebDavCollection.Capabilities) =
        if (capabilities.canCollectionSync)
            SyncAlgorithm.COLLECTION_SYNC
        else
            SyncAlgorithm.PROPFIND_REPORT

    override suspend fun uploadDirty(capabilities: WebDavCollection.Capabilities): Boolean {
        // local group housekeeping is needed regardless of whether we're actually uploading
        groupStrategy.resolveLocalGroupChanges()

        if (!localCollection.readOnly) {
            // preparing groups for upload is only relevant when local changes are pushed
            groupStrategy.beforeUploadDirty()
        }

        return super.uploadDirty(capabilities)
    }

    override suspend fun processDownload(result: WebDavCollection.MultiGetItem) {
        result.url.withExceptionContext {
            processCard(
                fileName = result.url.lastSegment,
                eTag = result.eTag,
                reader = StringReader(result.content),
                downloader = object : Contact.Downloader {
                    override suspend fun download(url: String, accepts: String): ByteArray? {
                        // retrieve external resource (like a photo) from a URL (not necessarily HTTP[S])
                        val retriever = resourceRetrieverFactory.create(
                            accountId,
                            remoteCollection.davCollection.location.host
                        )
                        return retriever.retrieve(url)
                    }
                }
            )
        }
    }

    override suspend fun postProcess() {
        groupStrategy.postProcess()
    }


    // helpers

    private suspend fun processCard(fileName: String, eTag: String, reader: Reader, downloader: Contact.Downloader) {
        logger.info("Processing CardDAV resource $fileName")

        // parse vCard
        val vCard = VCardParser().parse(reader)
        if (vCard == null) {
            logger.warning("Received vCard without data, ignoring")
            return
        }

        // map to Contact
        val newData = ContactReader.fromVCard(vCard, downloader)

        groupStrategy.verifyContactBeforeSaving(newData)

        var updated: LocalAddress? = null

        val existing = localCollection.findByName(fileName)
        if (existing == null) {
            // create new contact/group
            if (newData.group) {
                logger.info("Creating local group: $newData")
                val newGroup = localCollection.addGroup(newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                newGroup.withExceptionContext {
                    updated = newGroup
                }

            } else {
                logger.info("Creating local contact: $newData")
                val newContact = localCollection.addContact(newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                newContact.withExceptionContext {
                    updated = newContact
                }
            }

        } else {
            // update existing local contact/group
            logger.info("Updating $fileName in local address book: $newData")

            existing.withExceptionContext {
                if ((existing is LocalGroup && newData.group) || (existing is LocalContact && !newData.group)) {
                    // update contact / group

                    existing.update(
                        data = newData,
                        fileName = fileName,
                        eTag = eTag,
                        flags = LocalResource.FLAG_REMOTELY_PRESENT,
                        scheduleTag = null
                    )
                    updated = existing

                } else {
                    // group has become an individual contact or vice versa, delete and create with new type
                    existing.deleteLocal()

                    if (newData.group) {
                        logger.info("Creating local group (was contact before): $newData")
                        val newGroup = localCollection.addGroup(newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                        newGroup.withExceptionContext {
                            updated = newGroup
                        }

                    } else {
                        logger.info("Creating local contact (was group before): $newData")
                        val newContact = localCollection.addContact(newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                        newContact.withExceptionContext {
                            updated = newContact
                        }
                    }
                }
            }
        }

        // update hash code of updated contact, if applicable
        (updated as? LocalContact)?.let { updatedContact ->
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            dirtyVerifier.getOrNull()?.updateHashCode(localCollection, updatedContact)
        }
    }

}