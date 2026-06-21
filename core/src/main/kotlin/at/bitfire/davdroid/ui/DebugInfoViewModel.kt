/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.log.LogFileHandler
import at.bitfire.davdroid.ui.DebugInfoViewModel.Companion.FILE_DEBUG_INFO
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@HiltViewModel(assistedFactory = DebugInfoViewModel.Factory::class)
class DebugInfoViewModel @AssistedInject constructor(
    @Assisted private val details: DebugInfoDetails,
    @ApplicationContext val context: Context,
    private val debugInfoGenerator: DebugInfoGenerator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) : ViewModel() {

    data class DebugInfoDetails(
        val account: Account?,
        val syncDataType: String?,
        val cause: Throwable?,
        val localResource: String?,
        val remoteResource: String?,
        val logFile: File? = null,
        val timestamp: Long?
    )

    @AssistedFactory
    interface Factory {
        fun createWithDetails(details: DebugInfoDetails): DebugInfoViewModel
    }

    val cause: Throwable? = details.cause
    val localResource: String? = details.localResource
    val remoteResource: String? = details.remoteResource

    // use app-wide "verbose log" from LogFileHandler if no specific log file was provided
    val logFile: File? = (details.logFile ?: LogFileHandler.getDebugLogFile(context))?.takeIf { it.canRead() }

    data class UiState(
        val debugInfo: File? = null,
        val zipFile: File? = null,
        val zipInProgress: Boolean = false,
        val error: String? = null
    )

    var uiState by mutableStateOf(UiState())
        private set

    fun resetError() {
        uiState = uiState.copy(error = null)
    }

    fun resetZipFile() {
        uiState = uiState.copy(zipFile = null)
    }

    init {
        viewModelScope.launch(ioDispatcher) {
            generateDebugInfo(
                syncAccount = details.account,
                syncDataType = details.syncDataType,
                cause = details.cause,
                localResource = details.localResource,
                remoteResource = details.remoteResource,
                timestamp = details.timestamp
            )
        }
    }

    /**
     * Creates debug info and saves it to [FILE_DEBUG_INFO] in [LogFileHandler.debugDir]
     *
     * Note: Part of this method and all of its helpers (listed below) should probably be extracted in the future
     */
    private fun generateDebugInfo(
        syncAccount: Account?,
        syncDataType: String?,
        cause: Throwable?,
        localResource: String?,
        remoteResource: String?,
        timestamp: Long?
    ) {
        val debugInfoFile = File(LogFileHandler.debugDir(context), FILE_DEBUG_INFO)
        debugInfoFile.printWriter().use { writer ->
            debugInfoGenerator(
                syncAccount = syncAccount,
                syncDataType = syncDataType,
                cause = cause,
                localResource = localResource,
                remoteResource = remoteResource,
                timestamp = timestamp,
                writer = writer
            )
        }
        uiState = uiState.copy(debugInfo = debugInfoFile)
    }

    /**
     * Creates a ZIP file containing [FILE_DEBUG_INFO] and, if available, the [logFile];
     * falls back to logcat output otherwise.
     *
     * Note: Part of this method should probably be extracted to a more suitable location
     */
    fun generateZip() {
        try {
            uiState = uiState.copy(zipInProgress = true)

            val file = File(LogFileHandler.debugDir(context), "davx5-debug.zip")
            logger.fine("Writing debug info to ${file.absolutePath}")
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.setLevel(9)
                uiState.debugInfo?.let { debugInfo ->
                    zip.putNextEntry(ZipEntry("debug-info.txt"))
                    Files.copy(debugInfo, zip)
                    zip.closeEntry()
                }

                val logs = logFile
                if (logs != null) {
                    // verbose logs available
                    zip.putNextEntry(ZipEntry(logs.name))
                    Files.copy(logs, zip)
                    zip.closeEntry()
                } else {
                    // logcat (short logs)
                    try {
                        Runtime.getRuntime().exec("logcat -d").also { logcat ->
                            zip.putNextEntry(ZipEntry("logcat.txt"))
                            ByteStreams.copy(logcat.inputStream, zip)
                        }
                    } catch (e: Exception) {
                        logger.log(Level.SEVERE, "Couldn't attach logcat", e)
                    }
                }
            }

            // success, show ZIP file
            uiState = uiState.copy(zipFile = file)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Couldn't generate debug info ZIP", e)
            uiState = uiState.copy(error = e.localizedMessage)
        } finally {
            uiState = uiState.copy(zipInProgress = false)
        }
    }


    companion object {
        private const val FILE_DEBUG_INFO = "debug-info.txt"
    }

}