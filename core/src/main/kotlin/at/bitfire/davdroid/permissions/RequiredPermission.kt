/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import at.bitfire.synctools.storage.TaskProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import java.util.logging.Logger

data class RequiredPermission(
    val permission: String,
    val isRuntime: Boolean = true,
    val specialAccessIntent: ((Context) -> Intent)? = null
) {
    companion object {
        private val logger = Logger.getLogger("RequiredPermission")
    }

    fun isGranted(context: Context): Boolean {
        return if (isRuntime)
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        else {
            val permission = AppOpsManager.permissionToOp(permission) ?: run {
                logger.warning("Could not convert permission `$permission` to op")
                return false
            }
            val appOps = context.getSystemService<AppOpsManager>() ?: run {
                logger.warning("Could not get AppOpsManager")
                return false
            }
            appOps.checkOpNoThrow(permission, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }
    }
}

fun List<String>.asRuntimePermissions() = map { RequiredPermission(it) }

fun List<RequiredPermission>.permissions() = map { it.permission }
fun List<RequiredPermission>.runtimePermissions() = filter { it.isRuntime }
fun List<RequiredPermission>.specialPermissions() = filter { it.specialAccessIntent != null }

@ExperimentalPermissionsApi
fun List<RequiredPermission>.launchRequest(
    context: Context,
    state: MultiplePermissionsState
) {
    val allRuntimePermissionsGranted = filter { it.isRuntime }.all { it.isGranted(context) }
    if (!allRuntimePermissionsGranted)
        return state.launchMultiplePermissionRequest()

    val nextSpecialAccessPermissionRequest = firstOrNull { it.specialAccessIntent != null }
    val intent = nextSpecialAccessPermissionRequest?.specialAccessIntent?.invoke(context) ?: return
    context.startActivity(intent)
}

fun TaskProvider.ProviderName.requiredPermissions(): List<RequiredPermission> {
    return permissions.toList().asRuntimePermissions()
}
