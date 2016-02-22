/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;

import lombok.ToString;

@ToString
public class HomeSet {

    public long id, serviceID;
    public String URL;

    public static HomeSet fromDB(ContentValues values) {
        HomeSet homeSet = new HomeSet();
        homeSet.id = values.getAsLong(ServiceDB.HomeSets.ID);
        homeSet.serviceID = values.getAsLong(ServiceDB.HomeSets.SERVICE_ID);
        homeSet.URL = values.getAsString(ServiceDB.HomeSets.URL);
        return homeSet;
    }

}
