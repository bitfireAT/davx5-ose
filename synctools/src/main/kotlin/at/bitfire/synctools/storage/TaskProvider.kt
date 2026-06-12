/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.google.errorprone.annotations.MustBeClosed
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Basic properties of and access to supported task providers on
 * application/content provider level.
 */
object TaskProvider {

    enum class ProviderName(
        val authority: String,
        val packageName: String,
        val minVersionCode: Long,
        val minVersionName: String,
        val permissions: Array<String>
    ) {

        JtxBoard(
            authority = "at.techbee.jtx.provider",
            packageName = "at.techbee.jtx",
            minVersionCode = 210000000,
            minVersionName = "2.10.00",
            permissions = arrayOf("at.techbee.jtx.permission.READ", "at.techbee.jtx.permission.WRITE")
        ),
        TasksOrg(
            authority = "org.tasks.opentasks",
            packageName = "org.tasks",
            minVersionCode = 100000,
            minVersionName = "10.0",
            permissions = arrayOf("org.tasks.permission.READ_TASKS", "org.tasks.permission.WRITE_TASKS")
        ),
        OpenTasks(
            authority = "org.dmfs.tasks",
            packageName = "org.dmfs.tasks",
            minVersionCode = 103,
            minVersionName = "1.1.8.2",
            permissions = arrayOf("org.dmfs.permission.READ_TASKS", "org.dmfs.permission.WRITE_TASKS")
        );
    }


    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Acquires a content provider client for a given task provider after verifying that it's new
     * enough. The caller is responsible for closing the returned client.
     *
     * @param context will be used to acquire the content provider client
     * @param name task provider to acquire content provider for
     *
     * @return content provider client for the given task provider (`null` if not available)
     * @throws [ProviderTooOldException] if the tasks provider is installed, but doesn't meet the minimum version requirement
     */
    @MustBeClosed
    fun acquireRecentClient(context: Context, name: ProviderName): ContentProviderClient? =
        try {
            checkVersion(context, name)
            context.contentResolver.acquireContentProviderClient(name.authority)
        } catch (e: SecurityException) {
            logger.log(Level.WARNING, "Not allowed to access task provider", e)
            null
        } catch (_: PackageManager.NameNotFoundException) {
            logger.warning("Package ${name.packageName} not installed")
            null
        }

    /**
     * Checks the version code of an installed tasks provider.
     * @throws PackageManager.NameNotFoundException if the tasks provider is not installed
     * @throws [ProviderTooOldException] if the tasks provider is installed, but doesn't meet the minimum version requirement
     * */
    fun checkVersion(context: Context, name: ProviderName) {
        // check whether package is available with required minimum version
        val info = context.packageManager.getPackageInfo(name.packageName, 0)
        val installedVersionCode = PackageInfoCompat.getLongVersionCode(info)
        if (installedVersionCode < name.minVersionCode) {
            val exception = ProviderTooOldException(name, installedVersionCode, info.versionName)
            logger.log(Level.WARNING, "Task provider too old", exception)
            throw exception
        }
    }


    class ProviderTooOldException(
        val provider: ProviderName,
        installedVersionCode: Long,
        val installedVersionName: String?
    ): Exception("Package ${provider.packageName} has version $installedVersionName ($installedVersionCode), " +
            "required: ${provider.minVersionName} (${provider.minVersionCode})")

}
