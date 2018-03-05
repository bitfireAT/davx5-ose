/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v7.app.AppCompatDelegate
import at.bitfire.davdroid.log.Logger
<<<<<<< HEAD
=======
import at.bitfire.davdroid.ui.NotificationUtils
import kotlin.concurrent.thread
>>>>>>> 787260f1... Remove PermissionsActivity

class App: Application() {

    companion object {

        const val DISTRUST_SYSTEM_CERTIFICATES = "distrust_system_certs"
        const val OVERRIDE_PROXY = "override_proxy"
        const val OVERRIDE_PROXY_HOST = "override_proxy_host"
        const val OVERRIDE_PROXY_PORT = "override_proxy_port"

        const val OVERRIDE_PROXY_HOST_DEFAULT = "localhost"
        const val OVERRIDE_PROXY_PORT_DEFAULT = 8118


        fun getLauncherBitmap(context: Context): Bitmap? {
            val drawableLogo = if (android.os.Build.VERSION.SDK_INT >= 21)
                    context.getDrawable(R.mipmap.ic_launcher)
                else
                    @Suppress("deprecation")
                    context.resources.getDrawable(R.mipmap.ic_launcher)
            return if (drawableLogo is BitmapDrawable)
                drawableLogo.bitmap
            else
                null
        }

    }


    override fun onCreate() {
        super.onCreate()
        Logger.initialize(this)

        if (Build.VERSION.SDK_INT <= 21)
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        NotificationUtils.createChannels(this)

        // don't block UI for some background checks
        thread {
            // watch installed/removed apps
            val tasksFilter = IntentFilter()
            tasksFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
            tasksFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            tasksFilter.addDataScheme("package")
            registerReceiver(PackageChangedReceiver(), tasksFilter)

            // check whether a tasks app is currently installed
            PackageChangedReceiver.updateTaskSync(this)
        }
    }

}
