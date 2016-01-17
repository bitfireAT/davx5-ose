/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.resource.DavResourceFinder;

public class ServiceDB {

    public static class Services {
        public static final String
                _TABLE = "services",
                ID = "_id",
                ACCOUNT_NAME = "account_name",
                SERVICE = "service",
                PRINCIPAL = "principal",
                LAST_REFRESH = "last_refresh";

        // allowed values for SERVICE column
        public static final String
                SERVICE_CALDAV = "caldav",
                SERVICE_CARDDAV = "carddav";
    }

    public static class HomeSets {
        public static final String
                _TABLE = "homesets",
                ID = "_id",
                SERVICE_ID = "service_id",
                URL = "url";
    }

    public static class Collections {
        public static final String
                _TABLE = "collections",
                ID = "_id",
                SERVICE_ID = "service_id",
                URL = "url",
                DISPLAY_NAME = "display_name",
                DESCRIPTION = "description";

        public static ContentValues fromCollection(DavResourceFinder.Configuration.Collection collection) {
            ContentValues values = new ContentValues();
            values.put(DISPLAY_NAME, collection.getDisplayName());
            values.put(DESCRIPTION, collection.getDescription());
            return values;
        }
    }


    public static class OpenHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "services.db";
        private static final int DATABASE_VERSION = 1;

        public OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Constants.log.info("Creating services database");

            db.execSQL("CREATE TABLE " + Services._TABLE + "(" +
                    Services.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Services.ACCOUNT_NAME + " TEXT NOT NULL," +
                    Services.SERVICE + " TEXT NOT NULL," +
                    Services.PRINCIPAL + " TEXT NULL, " +
                    Services.LAST_REFRESH + " INTEGER NULL" +
                    ")");

            db.execSQL("CREATE TABLE " + HomeSets._TABLE + "(" +
                    HomeSets.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    HomeSets.SERVICE_ID + " INTEGER NOT NULL," +
                    HomeSets.URL + " TEXT NOT NULL" +
             ")");

            db.execSQL("CREATE TABLE " + Collections._TABLE + "(" +
                    Collections.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Collections.SERVICE_ID + " INTEGER NOT NULL," +
                    Collections.URL + " TEXT NOT NULL," +
                    Collections.DISPLAY_NAME + " TEXT NULL," +
                    Collections.DESCRIPTION + " TEXT NULL" +
            ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

}
