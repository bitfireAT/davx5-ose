/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.os.Parcel
import android.os.Parcelable
import at.bitfire.davdroid.model.Credentials
import java.net.URI

data class LoginInfo(
        @JvmField val uri: URI,
        @JvmField val credentials: Credentials
): Parcelable {

    constructor(uri: URI, userName: String? = null, password: String? = null, certificateAlias: String? = null):
        this(uri, Credentials(userName, password, certificateAlias))

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(uri)
        dest.writeSerializable(credentials)
    }

    companion object {

        @JvmField
        val CREATOR = object: Parcelable.Creator<LoginInfo> {
            override fun createFromParcel(source: Parcel) =
                    LoginInfo(
                        source.readSerializable() as URI,
                        source.readSerializable() as Credentials
                    )

            override fun newArray(size: Int) = arrayOfNulls<LoginInfo>(size)
        }

    }

}
