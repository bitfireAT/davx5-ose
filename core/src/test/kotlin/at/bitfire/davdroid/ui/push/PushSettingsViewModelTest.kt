/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.push

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.TurbineContext
import app.cash.turbine.turbineScope
import assertk.assertThat
import assertk.assertions.isEqualTo
import at.bitfire.davdroid.push.PushDistributorManager
import at.bitfire.davdroid.push.PushNotificationManager
import at.bitfire.davdroid.push.PushRegistrationManager
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.ui.push.PushSettingsContract.Event
import at.bitfire.davdroid.ui.push.PushSettingsContract.State.Content
import at.bitfire.davdroid.ui.push.PushSettingsContract.State.Loading
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment.getApplication
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class PushSettingsViewModelTest {

    private val ourPackageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    @Test
    fun `initial state is Loading`() = runPushSettingsScreenTest {
        // All work is done in runPushSettingsScreenTest()
    }

    @Test
    fun `initial content state`() {
        val initialSystemState = InitialSystemState()

        runPushSettingsScreenTest(initialSystemState) {
            screenContent {
                assertPushEnabledEquals(initialSystemState.isPushEnabled)
                assertDefaultDistributor(initialSystemState.defaultDistributor)
                assertSelectedDistributor(initialSystemState.selectedDistributor)
                assertPushDistributors(initialSystemState.pushDistributors)
            }
        }
    }

    @Test
    fun `display FCM push distributor entry when FCM is available`() {
        val pushDistributors = mapOf(
            ourPackageName to "FCM (Google Play)",
            PUSH_DISTRIBUTOR_1 to "Distributor 1",
        )

        runPushSettingsScreenTest(
            InitialSystemState(pushDistributors = pushDistributors, isFcmAvailable = true)
        ) {
            screenContent {
                assertPushDistributors(pushDistributors)
            }
        }
    }

    @Test
    fun `hide FCM push distributor entry when FCM is not available`() = runPushSettingsScreenTest(
        InitialSystemState(
            pushDistributors = mapOf(
                ourPackageName to "FCM (Google Play)",
                PUSH_DISTRIBUTOR_1 to "Distributor 1",
            ),
            isFcmAvailable = false,
        )
    ) {
        screenContent {
            assertPushDistributors(mapOf(PUSH_DISTRIBUTOR_1 to "Distributor 1"))
        }
    }

    @Test
    fun `push initially enabled then disabled then enabled`() = runPushSettingsScreenTest(
        InitialSystemState(isPushEnabled = true)
    ) {
        screenContent {
            assertPushEnabledEquals(true)
        }

        disablePush()

        screenContent {
            assertPushEnabledEquals(false)
        }

        enablePush()

        screenContent {
            assertPushEnabledEquals(true)
        }
    }

    @Test
    fun `push initially disabled then enabled then disabled`() = runPushSettingsScreenTest(
        InitialSystemState(isPushEnabled = false)
    ) {
        screenContent {
            assertPushEnabledEquals(false)
        }

        enablePush()

        screenContent {
            assertPushEnabledEquals(true)
        }

        disablePush()

        screenContent {
            assertPushEnabledEquals(false)
        }
    }

    @Test
    fun `select different push distributor`() = runPushSettingsScreenTest(
        InitialSystemState(selectedDistributor = PUSH_DISTRIBUTOR_1)
    ) {
        screenContent {
            assertSelectedDistributor(PUSH_DISTRIBUTOR_1)
        }

        selectDistributor(PUSH_DISTRIBUTOR_2)

        screenContent {
            assertSelectedDistributor(PUSH_DISTRIBUTOR_2)
        }
    }

    @Test
    fun `select default push distributor with no push distributor selected`() = runPushSettingsScreenTest(
        InitialSystemState(defaultDistributor = null, selectedDistributor = null)
    ) {
        screenContent {
            assertSelectedDistributor(null)
            assertDefaultDistributor(null)
        }

        selectDefaultDistributor(PUSH_DISTRIBUTOR_1)

        screenContent {
            assertSelectedDistributor(PUSH_DISTRIBUTOR_1)
            assertDefaultDistributor(PUSH_DISTRIBUTOR_1)
        }
    }

    @Test
    fun `select default push distributor with push distributor already selected`() = runPushSettingsScreenTest(
        InitialSystemState(defaultDistributor = null, selectedDistributor = PUSH_DISTRIBUTOR_2)
    ) {
        screenContent {
            assertSelectedDistributor(PUSH_DISTRIBUTOR_2)
            assertDefaultDistributor(null)
        }

        selectDefaultDistributor(PUSH_DISTRIBUTOR_1)

        screenContent {
            assertSelectedDistributor(PUSH_DISTRIBUTOR_2)
            assertDefaultDistributor(PUSH_DISTRIBUTOR_1)
        }
    }
}

private fun runPushSettingsScreenTest(
    initialSystemState: InitialSystemState = InitialSystemState(),
    block: suspend PushSettingsScreenRobot.() -> Unit,
) {
    runTest {
        val testScope = this
        turbineScope {
            val turbineScope = this

            PushSettingsScreenRobot(testScope, turbineScope, initialSystemState).apply {
                startScreen()

                block()
            }
        }
    }
}

private class PushSettingsScreenRobot(
    val testScope: TestScope,
    turbineScope: TurbineContext,
    initialSystemState: InitialSystemState,
) {
    private var isPushEnabled = initialSystemState.isPushEnabled
    private var defaultDistributor = initialSystemState.defaultDistributor
    private var selectedDistributor = initialSystemState.selectedDistributor
    private var pushDistributors = initialSystemState.pushDistributors
    private var isFcmAvailable = initialSystemState.isFcmAvailable

    private val collectionRepository = mockk<DavCollectionRepository>(relaxed = true)
    private val pushDistributorManager = mockk<PushDistributorManager>(relaxed = true) {
        every { isPushEnabled() } answers { isPushEnabled }
        every { setPushEnabled(any()) } answers { isPushEnabled = firstArg() }
        every { getDefaultDistributor() } answers { defaultDistributor }
        every { getSelectedDistributor() } answers { selectedDistributor }
        every { getDistributors() } answers {
            pushDistributors.map { it.key }
        }
        every { isFCMDistributorAvailable() } answers { isFcmAvailable }
    }
    private val pushRegistrationManager = mockk<PushRegistrationManager>(relaxed = true)
    private val pushNotificationManager = mockk<PushNotificationManager>(relaxed = true)

    private val customDispatcher = StandardTestDispatcher(testScope.testScheduler)
    private val viewModel = PushSettingsViewModel(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        applicationScope = testScope.backgroundScope,
        ioDispatcher = customDispatcher,
        collectionRepository = collectionRepository,
        pushDistributorManager = pushDistributorManager,
        pushRegistrationManager = pushRegistrationManager,
        pushNotificationManager = pushNotificationManager,
    )

    private val viewModelTurbine = with(turbineScope) {
        viewModel.uiState.testIn(testScope.backgroundScope)
    }

    init {
        addPushDistributorsToPackageManager()
    }

    /**
     * Launches the screen and asserts the first emitted state is [Loading].
     */
    suspend fun startScreen() {
        assertThat(viewModelTurbine.awaitItem()).isEqualTo(Loading)
    }

    fun enablePush() {
        viewModel.onEvent(Event.PushEnabled(true))
    }

    fun disablePush() {
        viewModel.onEvent(Event.PushEnabled(false))
    }

    fun selectDistributor(value: String) {
        viewModel.onEvent(Event.PushDistributorSelected(value))
    }

    fun selectDefaultDistributor(value: String) {
        defaultDistributor = value
        viewModel.onEvent(Event.DefaultPushDistributorSelected)
    }

    /**
     * Waits for the next state to be emitted, asserts it's a [Content] instance, and runs [block].
     */
    suspend fun screenContent(block: ContentAsserter.() -> Unit) {
        val state = viewModelTurbine.awaitItem()
        assertIs<Content>(state)

        ContentAsserter(state).block()
    }

    private fun addPushDistributorsToPackageManager() {
        val shadowPackageManager = shadowOf(getApplication().packageManager)
        for ((packageName, appName) in pushDistributors) {
            shadowPackageManager.installPackage(
                PackageInfo().apply {
                    applicationInfo = ApplicationInfo().apply {
                        name = appName
                    }
                    this.packageName = packageName
                }
            )
        }
    }
}

private class ContentAsserter(private val content: Content) {
    fun assertPushEnabledEquals(expected: Boolean) {
        assertThat(content.isPushEnabled).isEqualTo(expected)
    }

    fun assertDefaultDistributor(expected: String?) {
        assertThat(content.defaultPushDistributor).isEqualTo(expected)
    }

    fun assertSelectedDistributor(expected: String?) {
        assertThat(content.selectedPushDistributor).isEqualTo(expected)
    }

    fun assertPushDistributors(expected: Map<String, String>) {
        assertThat(content.pushDistributors)
            .transform { pushDistributorInfos ->
                pushDistributorInfos.associate { pushDistributorInfo ->
                    pushDistributorInfo.packageName to pushDistributorInfo.appName
                }
            }
            .isEqualTo(expected)
    }
}

private const val PUSH_DISTRIBUTOR_1 = "package.name.one"
private const val PUSH_DISTRIBUTOR_2 = "package.name.two"

private data class InitialSystemState(
    val isPushEnabled: Boolean = true,
    val defaultDistributor: String? = null,
    val selectedDistributor: String? = PUSH_DISTRIBUTOR_1,
    val pushDistributors: Map<String, String> = mapOf(
        PUSH_DISTRIBUTOR_1 to "Distributor 1",
        PUSH_DISTRIBUTOR_2 to "Distributor 2",
    ),
    val isFcmAvailable: Boolean = false,
)
