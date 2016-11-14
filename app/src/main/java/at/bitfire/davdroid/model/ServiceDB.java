/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

import at.bitfire.davdroid.App;
import lombok.Cleanup;

public class ServiceDB {

    public static class Settings {
        public static final String
                _TABLE = "settings",
                NAME = "setting",
                VALUE = "value";
    }

    public static class Services {
        public static final String
                _TABLE = "services",
                ID = "_id",
                ACCOUNT_NAME = "accountName",
                SERVICE = "service",
                PRINCIPAL = "principal";

        // allowed values for SERVICE column
        public static final String
                SERVICE_CALDAV = "caldav",
                SERVICE_CARDDAV = "carddav";
    }

    public static class HomeSets {
        public static final String
                _TABLE = "homesets",
                ID = "_id",
                SERVICE_ID = "serviceID",
                URL = "url";
    }

    public static class Collections {
        public static final String
                _TABLE = "collections",
                ID = "_id",
                SERVICE_ID = "serviceID",
                URL = "url",
                READ_ONLY = "readOnly",
                DISPLAY_NAME = "displayName",
                DESCRIPTION = "description",
                COLOR = "color",
                TIME_ZONE = "timezone",
                SUPPORTS_VEVENT = "supportsVEVENT",
                SUPPORTS_VTODO = "supportsVTODO",
                SYNC = "sync";
    }


    public static class OpenHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "services.db";
        private static final int DATABASE_VERSION = 1;

        public OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            if (Build.VERSION.SDK_INT < 16)
                db.execSQL("PRAGMA foreign_keys=ON;");
        }

        @Override
        @RequiresApi(16)
        public void onConfigure(SQLiteDatabase db) {
            setWriteAheadLoggingEnabled(true);
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            App.log.info("Creating database " + db.getPath());

            db.execSQL("CREATE TABLE " + Settings._TABLE + "(" +
                    Settings.NAME + " TEXT NOT NULL," +
                    Settings.VALUE + " TEXT NOT NULL" +
            ")");
            db.execSQL("CREATE UNIQUE INDEX settings_name ON " + Settings._TABLE + " (" + Settings.NAME + ")");

            db.execSQL("CREATE TABLE " + Services._TABLE + "(" +
                    Services.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Services.ACCOUNT_NAME + " TEXT NOT NULL," +
                    Services.SERVICE + " TEXT NOT NULL," +
                    Services.PRINCIPAL + " TEXT NULL" +
            ")");
            db.execSQL("CREATE UNIQUE INDEX services_account ON " + Services._TABLE + " (" + Services.ACCOUNT_NAME + "," + Services.SERVICE + ")");

            db.execSQL("CREATE TABLE " + HomeSets._TABLE + "(" +
                    HomeSets.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    HomeSets.SERVICE_ID + " INTEGER NOT NULL REFERENCES " + Services._TABLE +" ON DELETE CASCADE," +
                    HomeSets.URL + " TEXT NOT NULL" +
             ")");
            db.execSQL("CREATE UNIQUE INDEX homesets_service_url ON " + HomeSets._TABLE + "(" + HomeSets.SERVICE_ID + "," + HomeSets.URL + ")");

            db.execSQL("CREATE TABLE " + Collections._TABLE + "(" +
                    Collections.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Collections.SERVICE_ID + " INTEGER NOT NULL REFERENCES " + Services._TABLE +" ON DELETE CASCADE," +
                    Collections.URL + " TEXT NOT NULL," +
                    Collections.READ_ONLY + " INTEGER DEFAULT 0 NOT NULL," +
                    Collections.DISPLAY_NAME + " TEXT NULL," +
                    Collections.DESCRIPTION + " TEXT NULL," +
                    Collections.COLOR + " INTEGER NULL," +
                    Collections.TIME_ZONE + " TEXT NULL," +
                    Collections.SUPPORTS_VEVENT + " INTEGER NULL," +
                    Collections.SUPPORTS_VTODO + " INTEGER NULL," +
                    Collections.SYNC + " INTEGER DEFAULT 0 NOT NULL" +
            ")");
            db.execSQL("CREATE UNIQUE INDEX collections_service_url ON " + Collections._TABLE + "(" + Collections.SERVICE_ID + "," + Collections.URL + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // no different versions yet
        }


        public void dump(StringBuilder sb) {
            SQLiteDatabase db = getReadableDatabase();
            db.beginTransactionNonExclusive();

            // iterate through all tables
            @Cleanup Cursor cursorTables = db.query("sqlite_master", new String[] { "name" }, "type='table'", null, null, null, null);
            while (cursorTables.moveToNext()) {
                String table = cursorTables.getString(0);
                sb.append(table).append("\n");
                @Cleanup Cursor cursor = db.query(table, null, null, null, null, null, null);

                // print columns
                int cols = cursor.getColumnCount();
                sb.append("\t| ");
                for (int i = 0; i < cols; i++) {
                    sb.append(" ");
                    sb.append(cursor.getColumnName(i));
                    sb.append(" |");
                }
                sb.append("\n");

                // print rows
                while (cursor.moveToNext()) {
                    sb.append("\t| ");
                    for (int i = 0; i < cols; i++) {
                        sb.append(" ");
                        try {
                            String value = cursor.getString(i);
                            if (value != null)
                                sb.append(value
                                        .replace("\r", "<CR>")
                                        .replace("\n", "<LF>"));
                            else
                                sb.append("<null>");

                        } catch (SQLiteException e) {
                            sb.append("<unprintable>");
                        }
                        sb.append(" |");
                    }
                    sb.append("\n");
                }
                sb.append("----------\n");
            }
            db.endTransaction();
        }
    }


    public static void onRenameAccount(@NonNull SQLiteDatabase db, @NonNull String oldName, @NonNull String newName) {
        ContentValues values = new ContentValues(1);
        values.put(Services.ACCOUNT_NAME, newName);
        db.update(Services._TABLE, values, Services.ACCOUNT_NAME + "=?", new String[] { oldName });
    }

}
