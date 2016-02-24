/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup;

import android.os.Parcel;
import android.os.Parcelable;

import java.net.URI;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LoginCredentials implements Parcelable {
    public final URI uri;
    public final String userName, password;
    public final boolean authPreemptive;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(uri);
        dest.writeString(userName);
        dest.writeString(password);
        dest.writeValue(authPreemptive);
    }

    public static final Creator CREATOR = new Creator<LoginCredentials>() {
        @Override
        public LoginCredentials createFromParcel(Parcel source) {
            return new LoginCredentials(
                    (URI)source.readSerializable(),
                    source.readString(), source.readString(),
                    (boolean)source.readValue(null)
            );
        }

        @Override
        public LoginCredentials[] newArray(int size) {
            return new LoginCredentials[size];
        }
    };
}
