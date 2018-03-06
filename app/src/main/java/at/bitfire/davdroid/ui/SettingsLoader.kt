/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.support.v4.content.AsyncTaskLoader
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
                onContentChanged()
            }
        }
    }

    private var settingsSvc: ServiceConnection? = null
    var settings: ISettings? = null

    override fun onStartLoading() {
        if (settingsSvc != null)
            forceLoad()
        else {
            settingsSvc = object: ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                    settings = ISettings.Stub.asInterface(binder)
                    settings!!.registerObserver(settingsObserver)
                    onContentChanged()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    settings!!.unregisterObserver(settingsObserver)
                    settings = null
                }
            }
            context.bindService(Intent(context, Settings::class.java), settingsSvc, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onReset() {
        settingsSvc?.let {
            context.unbindService(it)
            settingsSvc = null
        }
    }

}