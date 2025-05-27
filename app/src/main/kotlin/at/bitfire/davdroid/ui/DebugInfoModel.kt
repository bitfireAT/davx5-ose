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
import at.bitfire.davdroid.log.LogFileHandler
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.ui.DebugInfoModel.Companion.FILE_DEBUG_INFO
import at.bitfire.davdroid.ui.DebugInfoModel.Companion.FILE_LOGS
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@HiltViewModel(assistedFactory = DebugInfoModel.Factory::class)
class DebugInfoModel @AssistedInject constructor(
    @Assisted private val details: DebugInfoDetails,
    @ApplicationContext val context: Context,
    private val debugInfoGenerator: DebugInfoGenerator,
    private val logger: Logger
) : ViewModel() {

    data class DebugInfoDetails(
        val account: Account?,
        val syncDataType: SyncDataType?,
        val cause: Throwable?,
        val localResource: String?,
        val remoteResource: String?,
        val logs: String?,
        val timestamp: Long?
    )

    @AssistedFactory
    interface Factory {
        fun createWithDetails(details: DebugInfoDetails): DebugInfoModel
    }

    data class UiState(
        val cause: Throwable? = null,
        val localResource: String? = null,
        val remoteResource: String? = null,
        val logFile: File? = null,
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
        // create debug info directory
        val debugDir = LogFileHandler.debugDir(context) ?: throw IOException("Couldn't create debug info directory")

        viewModelScope.launch(Dispatchers.Default) {
            // create log file from EXTRA_LOGS or log file
            if (details.logs != null) {
                val file = File(debugDir, FILE_LOGS)
                if (!file.exists() || file.canWrite()) {
                    file.printWriter().use { writer ->
                        writer.write(details.logs)
                    }
                    uiState = uiState.copy(logFile = file)
                } else
                    logger.warning("Can't write logs to $file")
            } else LogFileHandler.getDebugLogFile(context)?.let { debugLogFile ->
                if (debugLogFile.isFile && debugLogFile.canRead())
                    uiState = uiState.copy(logFile = debugLogFile)
            }

            uiState = uiState.copy(
                cause = details.cause,
                localResource = details.localResource,
                remoteResource = details.remoteResource
            )
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
     * Note: Part of this method and all of it's helpers (listed below) should probably be extracted in the future
     */
    private fun generateDebugInfo(
        syncAccount: Account?,
        syncDataType: SyncDataType?,
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
     * Creates the ZIP file containing both [FILE_DEBUG_INFO] and [FILE_LOGS].
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

                val logs = uiState.logFile
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
        private const val FILE_LOGS = "logs.txt"
    }

}