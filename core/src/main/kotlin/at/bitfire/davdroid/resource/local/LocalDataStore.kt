/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import android.accounts.Account
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.synctools.storage.LocalStorageClient
import javax.annotation.WillNotClose

/**
 * Represents a local data store for a specific collection type.
 * Manages creation, update, and deletion of collections of the given type.
 */
interface LocalDataStore<T: LocalCollection<*>> {

    /**
     * Content provider authority for the data store.
     */
    val authority: String

    /**
     * Acquires a [LocalStorageClient] for the data store. The result of this call should be passed to all other
     * methods of this class.
     *
     * **The caller is responsible for closing the `LocalStorageClient`!**
     *
     * @param throwOnMissingPermissions If `true`, the function will throw [SecurityException] if permissions are not
     *   granted.
     *
     * @return the `LocalStorageClient`, or `null` if the underlying content provider could not be acquired (or
     *   permissions are not granted and [throwOnMissingPermissions] is `false`)
     *
     * @throws SecurityException on missing permissions
     */
    fun acquireLocalStorageClient(throwOnMissingPermissions: Boolean = false): LocalStorageClient?

    /**
     * Creates a new local collection from the given (remote) collection info.
     *
     * @param client        the [LocalStorageClient]
     * @param fromCollection collection info
     *
     * @return the new local collection, or `null` if creation failed
     */
    suspend fun create(client: LocalStorageClient, fromCollection: Collection): T?

    /**
     * Returns all local collections of the data store, including those which don't have a corresponding remote
     * [Collection] entry.
     *
     * @param accountId [AccountId] of the account that the data store is associated with
     * @param client the [LocalStorageClient]
     *
     * @return a list of all local collections
     */
    fun getAll(accountId: AccountId, client: LocalStorageClient): List<T>

    /**
     * Retrieves a local collection by its database collection ID.
     *
     * @param accountId [AccountId] of the account associated with the collection.
     * @param client The [LocalStorageClient] used to access the data store.
     * @param dbCollectionId The database collection ID which the requested local collection corresponds to.
     *
     * @return The local collection with the specified DB collection ID, or `null` if not found.
     */
    @WorkerThread
    fun getByDbCollectionId(accountId: AccountId, client: LocalStorageClient, dbCollectionId: Long): T?

    /**
     * Updates the local collection with the data from the given (remote) collection info.
     *
     * @param accountId       [AccountId] of the account the collection belongs to
     * @param client          the [LocalStorageClient]
     * @param localCollection the local collection to update
     * @param fromCollection  collection info
     */
    fun update(accountId: AccountId, client: LocalStorageClient, localCollection: T, fromCollection: Collection)

    /**
     * Deletes the local collection.
     *
     * @param localCollection the local collection to delete
     */
    fun delete(localCollection: T)

    /**
     * Changes the account assigned to the containing data to another one.
     *
     * @param oldAccount The old account.
     * @param newAccount The new account.
     * @param client The [LocalStorageClient] for the local data store type or *null* when not needed for that data type.
     */
    fun updateAccount(oldAccount: Account, newAccount: Account, @WillNotClose client: LocalStorageClient?)

}