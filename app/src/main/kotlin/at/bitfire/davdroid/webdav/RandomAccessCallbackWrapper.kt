/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.os.Handler
import android.os.HandlerThread
import android.os.ProxyFileDescriptorCallback
import androidx.annotation.RequiresApi
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.webdav.RandomAccessCallbackWrapper.Companion.TIMEOUT_INTERVAL
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.MediaType
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.State
import ru.nsk.kstatemachine.state.finalState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.onExit
import ru.nsk.kstatemachine.state.onFinished
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import java.util.Timer
import java.util.TimerTask
import java.util.logging.Logger
import kotlin.concurrent.schedule

/**
 * (2021/12/02) Currently Android's `StorageManager.openProxyFileDescriptor` has a memory leak:
 * the given callback is registered in `com.android.internal.os.AppFuseMount` (which adds it to
 * a [Map]), but is not unregistered anymore. So it stays in the memory until the whole mount
 * is unloaded. See https://issuetracker.google.com/issues/208788568
 *
 * Use this wrapper to
 *
 * - ensure that all memory is released as soon as [onRelease] is called,
 * - provide timeout functionality: [RandomAccessCallback] will be closed when not
 *
 * used for more than [TIMEOUT_INTERVAL] ms and re-created when necessary.
 *
 * @param httpClient    HTTP client – [RandomAccessCallbackWrapper] is responsible to close it
 */
@RequiresApi(26)
class RandomAccessCallbackWrapper @AssistedInject constructor(
    @Assisted private val httpClient: HttpClient,
    @Assisted private val url: HttpUrl,
    @Assisted private val mimeType: MediaType?,
    @Assisted private val headResponse: HeadResponse,
    @Assisted private val externalScope: CoroutineScope,
    private val logger: Logger,
    private val callbackFactory: RandomAccessCallback.Factory
): ProxyFileDescriptorCallback() {

    companion object {
        const val TIMEOUT_INTERVAL = 15000L
    }

    @AssistedFactory
    interface Factory {
        fun create(httpClient: HttpClient, url: HttpUrl, mimeType: MediaType?, headResponse: HeadResponse, externalScope: CoroutineScope): RandomAccessCallbackWrapper
    }

    sealed class Events {
        object Transfer : Event
        object NowIdle : Event
        object GoStandby : Event
        object Close : Event
    }
    /* We don't use a sealed class for states here because the states would then be singletons, while we can have
    multiple instances of the state machine (which require multiple instances of the states, too). */
    private val machine = createStdLibStateMachine {
        lateinit var activeIdleState: State
        lateinit var activeTransferringState: State
        lateinit var standbyState: State
        lateinit var closedState: State

        initialState("active") {
            onEntry {
                _callback = callbackFactory.create(httpClient, url, mimeType, headResponse, externalScope)
            }
            onExit {
                _callback?.onRelease()
                _callback = null
            }

            transitionOn<Events.GoStandby> { targetState = { standbyState } }
            transitionOn<Events.Close> { targetState = { closedState } }

            // active has two nested states: transferring (I/O running) and idle (starts timeout timer)
            activeIdleState = initialState("idle") {
                val timer: Timer = Timer(true)
                var timeout: TimerTask? = null

                onEntry {
                    timeout = timer.schedule(TIMEOUT_INTERVAL) {
                        machine.processEventBlocking(Events.GoStandby)
                    }
                }
                onExit {
                    timeout?.cancel()
                    timeout = null
                }
                onFinished {
                    timer.cancel()
                }

                transitionOn<Events.Transfer> { targetState = { activeTransferringState } }
            }

            activeTransferringState = state("transferring") {
                transitionOn<Events.NowIdle> { targetState = { activeIdleState } }
            }
        }

        standbyState = state("standby") {
            transitionOn<Events.Transfer> { targetState = { activeTransferringState } }
            transitionOn<Events.NowIdle> { targetState = { activeIdleState } }
            transitionOn<Events.Close> { targetState = { closedState } }
        }

        closedState = finalState("closed")
        onFinished {
            shutdown()
        }

        logger = StateMachine.Logger { message ->
            this@RandomAccessCallbackWrapper.logger.finer(message())
        }
    }

    private val workerThread = HandlerThread(javaClass.simpleName).apply { start() }
    val workerHandler: Handler = Handler(workerThread.looper)

    private var _callback: RandomAccessCallback? = null

    fun<T> requireCallback(block: (callback: RandomAccessCallback) -> T): T {
        machine.processEventBlocking(Events.Transfer)
        try {
            return block(_callback ?: throw IllegalStateException())
        } finally {
            machine.processEventBlocking(Events.NowIdle)
        }
    }


    /// states ///

    @Synchronized
    private fun shutdown() {
        httpClient.close()
        workerThread.quit()
    }


    /// delegating implementation of ProxyFileDescriptorCallback ///

    @Synchronized
    override fun onFsync() { /* not used */ }

    @Synchronized
    override fun onGetSize() =
        requireCallback { it.onGetSize() }

    @Synchronized
    override fun onRead(offset: Long, size: Int, data: ByteArray) =
        requireCallback { it.onRead(offset, size, data) }

    @Synchronized
    override fun onWrite(offset: Long, size: Int, data: ByteArray) =
        requireCallback { it.onWrite(offset, size, data) }

    @Synchronized
    override fun onRelease() {
        machine.processEventBlocking(Events.Close)
    }

}