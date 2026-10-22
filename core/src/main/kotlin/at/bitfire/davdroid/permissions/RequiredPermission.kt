/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleResumeEffect
import at.bitfire.synctools.storage.TaskProvider
import java.util.logging.Logger

data class RequiredPermission(
    val permission: String,
    val isRuntime: Boolean = true,
    val specialAccessIntent: ((Context) -> Intent)? = null
) {
    companion object {
        private val logger = Logger.getLogger("RequiredPermission")
    }

    val isSpecialAccess = specialAccessIntent != null

    /**
     * Checks whether the permission is granted.
     *
     * This automatically handles both runtime and special access permissions.
     */
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

/**
 * Converts a list of Android permission [android.Manifest.permission] into a list of [RequiredPermission].
 *
 * The list is not checked, so it's the caller's responsibility to make sure the given permissions are valid.
 */
fun List<String>.asRuntimePermissions() = map { RequiredPermission(it) }

/**
 * Converts the [permissions] list into [RequiredPermission].
 */
fun TaskProvider.ProviderName.requiredPermissions(): List<RequiredPermission> {
    return permissions.toList().asRuntimePermissions()
}

@Stable
open class PermissionsStatus(
    val permissions: List<RequiredPermission>,
    val granted: List<RequiredPermission>,
    val denied: List<RequiredPermission>
) {
    val allRuntimePermissionsGranted = permissions.filter { it.isRuntime }.all { granted.contains(it) }
    val allSpecialPermissionsGranted = permissions.filter { it.isSpecialAccess }.all { granted.contains(it) }
    val allPermissionsGranted = allRuntimePermissionsGranted && allSpecialPermissionsGranted

    fun withLauncher(
        launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>
    ): PermissionsStatusWithLauncher {
        return PermissionsStatusWithActivityLauncher(permissions, granted, denied, launcher)
    }
}

abstract class PermissionsStatusWithLauncher(
    permissions: List<RequiredPermission>,
    granted: List<RequiredPermission>,
    denied: List<RequiredPermission>,
): PermissionsStatus(permissions, granted, denied) {
    abstract fun launch(context: Context)
}

class PermissionsStatusWithLauncherPreview: PermissionsStatusWithLauncher(
    permissions = emptyList(),
    granted = emptyList(),
    denied = emptyList()
) {
    override fun launch(context: Context) {
        // no-op
    }
}

class PermissionsStatusWithActivityLauncher(
    permissions: List<RequiredPermission>,
    granted: List<RequiredPermission>,
    denied: List<RequiredPermission>,
    private val launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>
): PermissionsStatusWithLauncher(permissions, granted, denied) {
    override fun launch(context: Context) {
        val missingRuntimePermissions = denied.filter { it.isRuntime }
        val missingSpecialPermissions = denied.filter { it.isSpecialAccess }
        if (missingRuntimePermissions.isNotEmpty()) {
            launcher.launch(missingRuntimePermissions.map { it.permission }.toTypedArray())
            return
        }
        val intent = missingSpecialPermissions.firstOrNull()?.specialAccessIntent?.invoke(context) ?: return
        context.startActivity(intent)
    }
}

@Composable
fun List<RequiredPermission>.rememberPermissionsRequest(): PermissionsStatusWithLauncher {
    if (LocalInspectionMode.current) {
        return PermissionsStatusWithLauncherPreview()
    }

    val context = LocalContext.current

    fun calculateCurrentRequestStatus(): PermissionsStatus {
        val granted = filter { it.isGranted(context) }
        val denied = filterNot { granted.contains(it) }
        return PermissionsStatus(this, granted, denied)
    }

    var status by remember { mutableStateOf(calculateCurrentRequestStatus()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // When we get a response from the permission request, refresh status
        status = calculateCurrentRequestStatus()
    }

    LifecycleResumeEffect(Unit) {
        // When app is resumed, refresh status
        status = calculateCurrentRequestStatus()
        onPauseOrDispose { /*nothing*/ }
    }

    return status.withLauncher(launcher)
}
