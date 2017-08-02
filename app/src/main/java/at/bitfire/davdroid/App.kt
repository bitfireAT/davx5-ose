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
import at.bitfire.davdroid.log.Logger

class App: Application() {

    companion object {

        @JvmField val DISTRUST_SYSTEM_CERTIFICATES = "distrustSystemCerts"
        @JvmField val LOG_TO_EXTERNAL_STORAGE = "logToExternalStorage"
        @JvmField val OVERRIDE_PROXY = "overrideProxy"
        @JvmField val OVERRIDE_PROXY_HOST = "overrideProxyHost"
        @JvmField val OVERRIDE_PROXY_PORT = "overrideProxyPort"

        @JvmField val OVERRIDE_PROXY_HOST_DEFAULT = "localhost"
        @JvmField val OVERRIDE_PROXY_PORT_DEFAULT = 8118

        lateinit var addressBookAccountType: String
        lateinit var addressBooksAuthority: String

        @JvmStatic
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

        CustomCertificates.reinitCertManager(this)
        Logger.reinitLogger(this)

        addressBookAccountType = getString(R.string.account_type_address_book)
        addressBooksAuthority = getString(R.string.address_books_authority)
    }

}
