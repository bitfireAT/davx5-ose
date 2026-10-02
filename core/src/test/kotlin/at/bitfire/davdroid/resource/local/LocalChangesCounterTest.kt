/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import at.bitfire.dav4jvm.ktor.toUrlOrNull
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.synctools.storage.LocalStorageClient
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger

class LocalChangesCounterTest {

    @get:Rule
    val mockKRule = MockKRule(this)

    @MockK
    lateinit var accountId: AccountId

    @MockK
    lateinit var addressBookStore: LocalAddressBookStore

    @MockK
    lateinit var calendarStore: LocalCalendarStore

    @MockK
    lateinit var tasksAppManager: TasksAppManager

    @MockK
    lateinit var taskListStore: LocalTaskListStore

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var counter: LocalChangesCounter

    @Before
    fun setUp() {
        every { tasksAppManager.getDataStore() } returns null

        counter = LocalChangesCounter(
            ioDispatcher = testDispatcher,
            localAddressBookStore = { addressBookStore },
            localCalendarStore = { calendarStore },
            logger = Logger.getLogger(javaClass.name),
            tasksAppManager = { tasksAppManager }
        )
    }


    @Test
    fun `countUnsyncedChanges() for address book sums modified and deleted entries`() = runTest(testDispatcher) {
        val client = mockStore(addressBookStore, modified = 2, deleted = 3)

        val result = counter.countUnsyncedChanges(accountId, collection(Collection.TYPE_ADDRESSBOOK))

        assertEquals(5, result)
        verify { client.close() }
    }

    @Test
    fun `countUnsyncedChanges() for calendar also counts tasks app entries`() = runTest(testDispatcher) {
        mockStore(calendarStore, modified = 1, deleted = 0)
        mockStore(taskListStore, modified = 0, deleted = 4)
        every { tasksAppManager.getDataStore() } returns taskListStore

        val result = counter.countUnsyncedChanges(accountId, collection(Collection.TYPE_CALENDAR))

        assertEquals(5, result)
    }

    @Test
    fun `countUnsyncedChanges() skips data stores that can't be accessed`() = runTest(testDispatcher) {
        mockStore(calendarStore, modified = 1, deleted = 1)
        every { taskListStore.acquireLocalStorageClient(any()) } returns null
        every { taskListStore.authority } returns "tasks"
        every { tasksAppManager.getDataStore() } returns taskListStore

        val result = counter.countUnsyncedChanges(accountId, collection(Collection.TYPE_CALENDAR))

        assertEquals(2, result)
    }

    @Test
    fun `countUnsyncedChanges() without local collection returns 0`() = runTest(testDispatcher) {
        val client = mockk<LocalStorageClient>(relaxed = true)
        every { addressBookStore.acquireLocalStorageClient(any()) } returns client
        every { addressBookStore.getByDbCollectionId(accountId, client, COLLECTION_ID) } returns null

        val result = counter.countUnsyncedChanges(accountId, collection(Collection.TYPE_ADDRESSBOOK))

        assertEquals(0, result)
    }

    @Test
    fun `countUnsyncedChanges() for webcal subscription returns 0`() = runTest(testDispatcher) {
        val result = counter.countUnsyncedChanges(accountId, collection(Collection.TYPE_WEBCAL))

        assertEquals(0, result)
        verify(exactly = 0) { calendarStore.acquireLocalStorageClient(any()) }
    }


    // helpers

    private fun collection(type: String) = Collection(
        id = COLLECTION_ID,
        type = type,
        url = "https://example.com/collection/".toUrlOrNull()!!
    )

    private inline fun <reified T : LocalCollection<*>> mockStore(
        store: LocalDataStore<T>,
        modified: Int,
        deleted: Int
    ): LocalStorageClient {
        val client = mockk<LocalStorageClient>(relaxed = true)
        val localCollection = mockk<T> {
            every { countModified() } returns modified
            every { countDeleted() } returns deleted
        }
        every { store.acquireLocalStorageClient(any()) } returns client
        every { store.getByDbCollectionId(accountId, client, COLLECTION_ID) } returns localCollection
        return client
    }

    companion object {
        private const val COLLECTION_ID = 42L
    }

}
