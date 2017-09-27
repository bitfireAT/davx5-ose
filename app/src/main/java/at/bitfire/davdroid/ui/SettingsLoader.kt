/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.*
import android.os.Handler
import android.os.IBinder
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.settings.ISettingsObserver
import at.bitfire.davdroid.settings.Settings

abstract class SettingsLoader<T>(
        context: Context
): AsyncTaskLoader<T>(context) {

    val handler = Handler()
    val settingsObserver = object: ISettingsObserver.Stub() {
        override fun onSettingsChanged() {
            handler.post {
                forceLoad()
            }
        }
    }

    var settings: ISettings? = null
    private val settingsSvc = object: ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
            settings = ISettings.Stub.asInterface(binder)
            settings!!.registerObserver(settingsObserver)
            forceLoad()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            settings!!.unregisterObserver(settingsObserver)
            settings = null
        }

    }

    override fun onStartLoading() {
        context.bindService(Intent(context, Settings::class.java), settingsSvc, Context.BIND_AUTO_CREATE)
    }

    override fun onStopLoading() {
        context.unbindService(settingsSvc)
    }

}