/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.permissions

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import java.util.logging.Logger

data class RequiredPermission(
    val permission: String,
    val isRuntime: Boolean = true,
    val specialAccessOperation: SpecialAccessOperation? = null
) {
    companion object {
        private val logger = Logger.getLogger("RequiredPermission")
    }

    /**
     * Defines how to request a special permission.
     * @param message An optional message to display to the user in a toast when launching the intent, to guide them
     * on granting the permission.
     * @param intent The intent to launch to request the permission to the user.
     */
    class SpecialAccessOperation(
        val message: (Context) -> String? = { null },
        val intent: (Context) -> Intent
    ) {
        /**
         * Shows the [message] if any in a toast, and launches [intent] with [context].
         */
        @UiThread
        fun launch(context: Context) {
            message(context)?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }

            try {
                val intent = intent(context)
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // cannot open activity, ignore
            }
        }
    }

    val isSpecialAccess = specialAccessOperation != null

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
