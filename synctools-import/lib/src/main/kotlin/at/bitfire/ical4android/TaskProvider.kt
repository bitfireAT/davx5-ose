/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.annotation.SuppressLint
import android.content.ContentProviderClient
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat

import java.io.Closeable
import java.util.logging.Level
import java.util.logging.Logger
import org.dmfs.tasks.contract.TaskContract


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
        JtxBoard("at.techbee.jtx.provider", "at.techbee.jtx", 210000000, "2.10.00", PERMISSION_JTX_READ, PERMISSION_JTX_WRITE),
        TasksOrg("org.tasks.opentasks", "org.tasks", 100000, "10.0", PERMISSION_TASKS_ORG_READ, PERMISSION_TASKS_ORG_WRITE),
        OpenTasks("org.dmfs.tasks", "org.dmfs.tasks", 103, "1.1.8.2", PERMISSION_OPENTASKS_READ, PERMISSION_OPENTASKS_WRITE);

        companion object {
            fun fromAuthority(authority: String): ProviderName {
                for (provider in values())
                    if (provider.authority == authority)
                        return provider
                throw IllegalArgumentException("Unknown tasks authority $authority")
            }
        }

        val permissions: Array<String>
            get() = arrayOf(readPermission, writePermission)
    }


    companion object {

        private val logger
            get() = Logger.getLogger(TaskProvider::javaClass.name)

        val TASK_PROVIDERS = listOf(
                ProviderName.OpenTasks,
                ProviderName.TasksOrg,
                ProviderName.JtxBoard
        )

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

        fun fromProviderClient(
                context: Context,
                provider: ProviderName,
                client: ContentProviderClient
        ): TaskProvider {
            checkVersion(context, provider)
            return TaskProvider(provider, client)
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


    fun taskListsUri() = TaskContract.TaskLists.getContentUri(name.authority)!!
    fun syncStateUri() = TaskContract.SyncState.getContentUri(name.authority)!!

    fun tasksUri() = TaskContract.Tasks.getContentUri(name.authority)!!
    fun propertiesUri() = TaskContract.Properties.getContentUri(name.authority)!!
    fun alarmsUri() = TaskContract.Alarms.getContentUri(name.authority)!!
    fun categoriesUri() = TaskContract.Categories.getContentUri(name.authority)!!


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
