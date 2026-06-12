/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.annotation.SuppressLint
import android.content.ContentProviderClient
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import org.dmfs.tasks.contract.TaskContract
import java.io.Closeable
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Basic properties of and access to supported task providers on
 * application/content provider level.
 */
class TaskProvider private constructor(
    val name: ProviderName,
    val client: ContentProviderClient
): Closeable {

    enum class ProviderName(
        val authority: String,
        val packageName: String,
        val minVersionCode: Long,
        val minVersionName: String,
        private val readPermission: String,
        private val writePermission: String
    ) {

        JtxBoard(
            authority = "at.techbee.jtx.provider",
            packageName = "at.techbee.jtx",
            minVersionCode = 210000000,
            minVersionName = "2.10.00",
            readPermission = PERMISSION_JTX_READ,
            writePermission = PERMISSION_JTX_WRITE
        ),
        TasksOrg(
            authority = "org.tasks.opentasks",
            packageName = "org.tasks",
            minVersionCode = 100000,
            minVersionName = "10.0",
            readPermission = PERMISSION_TASKS_ORG_READ,
            writePermission = PERMISSION_TASKS_ORG_WRITE
        ),
        OpenTasks(
            authority = "org.dmfs.tasks",
            packageName = "org.dmfs.tasks",
            minVersionCode = 103,
            minVersionName = "1.1.8.2",
            readPermission = PERMISSION_OPENTASKS_READ,
            writePermission = PERMISSION_OPENTASKS_WRITE
        );

        val permissions: Array<String>
            get() = arrayOf(readPermission, writePermission)
    }


    companion object {

        private val logger
            get() = Logger.getLogger(TaskProvider::class.java.name)

        const val PERMISSION_OPENTASKS_READ = "org.dmfs.permission.READ_TASKS"
        const val PERMISSION_OPENTASKS_WRITE = "org.dmfs.permission.WRITE_TASKS"
        val PERMISSIONS_OPENTASKS = arrayOf(PERMISSION_OPENTASKS_READ, PERMISSION_OPENTASKS_WRITE)

        const val PERMISSION_TASKS_ORG_READ = "org.tasks.permission.READ_TASKS"
        const val PERMISSION_TASKS_ORG_WRITE = "org.tasks.permission.WRITE_TASKS"
        val PERMISSIONS_TASKS_ORG = arrayOf(PERMISSION_TASKS_ORG_READ, PERMISSION_TASKS_ORG_WRITE)

        const val PERMISSION_JTX_READ = "at.techbee.jtx.permission.READ"
        const val PERMISSION_JTX_WRITE = "at.techbee.jtx.permission.WRITE"
        val PERMISSIONS_JTX = arrayOf(PERMISSION_JTX_READ, PERMISSION_JTX_WRITE)

        /**
         * Acquires a content provider for a given task provider. The content provider will
         * be released when the TaskProvider is closed with [close].
         * @param context will be used to acquire the content provider client
         * @param name task provider to acquire content provider for; *null* to try all supported providers
         * @return content provider for the given task provider (may be *null*)
         * @throws [ProviderTooOldException] if the tasks provider is installed, but doesn't meet the minimum version requirement
         */
        @SuppressLint("Recycle")
        fun acquire(context: Context, name: ProviderName? = null): TaskProvider? {
            val providers =
                    name?.let { arrayOf(it) }       // provider name given? create array from it
                    ?: ProviderName.values()        // otherwise, try all providers
            for (provider in providers)
                try {
                    checkVersion(context, provider)

                    val client = context.contentResolver.acquireContentProviderClient(provider.authority)
                    if (client != null)
                        return TaskProvider(provider, client)
                } catch(e: SecurityException) {
                    logger.log(Level.WARNING, "Not allowed to access task provider", e)
                } catch(e: PackageManager.NameNotFoundException) {
                    logger.warning("Package ${provider.packageName} not installed")
                }
            return null
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

    }

    fun tasksUri() = TaskContract.Tasks.getContentUri(name.authority)!!


    override fun close() {
        client.close()
    }


    class ProviderTooOldException(
        val provider: ProviderName,
        installedVersionCode: Long,
        val installedVersionName: String?
    ): Exception("Package ${provider.packageName} has version $installedVersionName ($installedVersionCode), " +
            "required: ${provider.minVersionName} (${provider.minVersionCode})")

}
