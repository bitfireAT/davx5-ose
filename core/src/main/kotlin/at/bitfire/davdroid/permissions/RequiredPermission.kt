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
