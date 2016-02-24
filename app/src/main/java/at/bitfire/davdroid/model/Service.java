/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;

public class Service {

    public long id;
    public String accountName, service, principal;
    public long lastRefresh;


    public static Service fromDB(ContentValues values) {
        Service service = new Service();
        service.id = values.getAsLong(ServiceDB.Services.ID);
        service.accountName = values.getAsString(ServiceDB.Services.ACCOUNT_NAME);
        service.service = values.getAsString(ServiceDB.Services.SERVICE);
        service.principal = values.getAsString(ServiceDB.Services.PRINCIPAL);
        //FIXME service.lastRefresh = values.getAsLong(ServiceDB.Services.LAST_REFRESH);
        return service;
    }

}
