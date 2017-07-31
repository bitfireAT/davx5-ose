/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.content.Context
import android.os.Build
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.Settings
import okhttp3.internal.tls.OkHostnameVerifier
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory

object CustomCertificates {

    @JvmField var certManager: CustomCertManager? = null
    var sslSocketFactoryCompat: SSLSocketFactory? = null
    var hostnameVerifier: HostnameVerifier? = null

    fun reinitCertManager(context: Context) {
        if (BuildConfig.customCerts) {
            certManager?.close()

            ServiceDB.OpenHelper(context).use { dbHelper ->
                val settings = Settings(dbHelper.readableDatabase)

                val mgr = CustomCertManager(context.applicationContext, !settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false))
                certManager = mgr
                hostnameVerifier = mgr.hostnameVerifier(OkHostnameVerifier.INSTANCE)

                sslSocketFactoryCompat = if (Build.VERSION.SDK_INT >= 23)
                    SSLSocketFactory.getDefault() as? SSLSocketFactory?
                else
                    SSLSocketFactoryCompat(mgr)
            }
        }
    }

}