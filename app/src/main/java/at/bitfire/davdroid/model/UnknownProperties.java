/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.provider.ContactsContract.RawContacts;

public class UnknownProperties {

    public static final String CONTENT_ITEM_TYPE = "x.davdroid/unknown-properties";

    public static final String
            MIMETYPE = RawContacts.Data.MIMETYPE,
            RAW_CONTACT_ID = RawContacts.Data.RAW_CONTACT_ID,
            UNKNOWN_PROPERTIES = RawContacts.Data.DATA1;

}
