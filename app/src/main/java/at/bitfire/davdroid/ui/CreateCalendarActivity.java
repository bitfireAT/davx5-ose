/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatSpinner;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.util.TimeZones;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.ical4android.DateUtils;
import lombok.Cleanup;
import okhttp3.HttpUrl;
import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.AmbilWarnaSquare;

public class CreateCalendarActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<CreateCalendarActivity.AccountInfo> {
    public static final String EXTRA_ACCOUNT = "account";

    protected Account account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_create_calendar);
        final View colorSquare = findViewById(R.id.color);
        colorSquare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AmbilWarnaDialog(CreateCalendarActivity.this, ((ColorDrawable)colorSquare.getBackground()).getColor(), true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                    @Override
                    public void onCancel(AmbilWarnaDialog dialog) {
                    }

                    @Override
                    public void onOk(AmbilWarnaDialog dialog, int color) {
                        colorSquare.setBackgroundColor(color);
                    }
                }).show();
            }
        });

        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_create_calendar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, AccountActivity.class);
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT_NAME, account.name);
            NavUtils.navigateUpTo(this, intent);
            return true;
        }
        return false;
    }

    public void onCreateCalendar(MenuItem item) {
        boolean ok = true;
        CollectionInfo info = new CollectionInfo();

        AppCompatSpinner spinner = (AppCompatSpinner)findViewById(R.id.home_set);
        String homeSet = (String)spinner.getSelectedItem();

        EditText edit = (EditText)findViewById(R.id.display_name);
        info.displayName = edit.getText().toString();
        if (TextUtils.isEmpty(info.displayName)) {
            edit.setError("Enter a calendar title.");
            ok = false;
        }

        edit = (EditText)findViewById(R.id.description);
        info.description = StringUtils.trimToNull(edit.getText().toString());

        View view = findViewById(R.id.color);
        info.color = ((ColorDrawable)view.getBackground()).getColor();

        spinner = (AppCompatSpinner)findViewById(R.id.time_zone);
        net.fortuna.ical4j.model.TimeZone tz = DateUtils.tzRegistry.getTimeZone((String)spinner.getSelectedItem());
        if (tz != null) {
            Calendar cal = new Calendar();
            cal.getComponents().add(tz.getVTimeZone());
            info.timeZone = cal.toString();
        }

        RadioGroup typeGroup = (RadioGroup)findViewById(R.id.type);
        switch (typeGroup.getCheckedRadioButtonId()) {
            case R.id.type_events:
                info.supportsVEVENT = true;
                break;
            case R.id.type_tasks:
                info.supportsVTODO = true;
                break;
            case R.id.type_events_and_tasks:
                info.supportsVEVENT = true;
                info.supportsVTODO = true;
                break;
        }

        if (ok) {
            info.type = CollectionInfo.Type.CALENDAR;
            info.url = HttpUrl.parse(homeSet).resolve(UUID.randomUUID().toString() + "/").toString();
            CreateCollectionFragment.newInstance(account, info).show(getSupportFragmentManager(), null);
        }
    }


    @Override
    public Loader<AccountInfo> onCreateLoader(int id, Bundle args) {
        return new AccountInfoLoader(this, account);
    }

    @Override
    public void onLoadFinished(Loader<AccountInfo> loader, AccountInfo info) {
        AppCompatSpinner spinner = (AppCompatSpinner)findViewById(R.id.time_zone);
        String[] timeZones = TimeZone.getAvailableIDs();
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, timeZones));
        // select system time zone
        String defaultTimeZone = TimeZone.getDefault().getID();
        for (int i = 0; i < timeZones.length; i++)
            if (timeZones[i].equals(defaultTimeZone)) {
                spinner.setSelection(i);
                break;
            }

        if (info != null) {
            spinner = (AppCompatSpinner)findViewById(R.id.home_set);
            spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, info.homeSets));
        }
    }

    @Override
    public void onLoaderReset(Loader<AccountInfo> loader) {
    }

    protected static class AccountInfo {
        List<String> homeSets = new LinkedList<>();
    }

    protected static class AccountInfoLoader extends AsyncTaskLoader<AccountInfo> {
        private final Account account;
        private final ServiceDB.OpenHelper dbHelper;

        public AccountInfoLoader(Context context, Account account) {
            super(context);
            this.account = account;
            dbHelper = new ServiceDB.OpenHelper(context);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public AccountInfo loadInBackground() {
            final AccountInfo info = new AccountInfo();

            // find DAV service and home sets
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            try {
                @Cleanup Cursor cursorService = db.query(ServiceDB.Services._TABLE, new String[] { ServiceDB.Services.ID },
                        ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?",
                        new String[] { account.name, ServiceDB.Services.SERVICE_CALDAV }, null, null, null);
                if (!cursorService.moveToNext())
                    return null;
                String strServiceID = cursorService.getString(0);

                @Cleanup Cursor cursorHomeSets = db.query(ServiceDB.HomeSets._TABLE, new String[] { ServiceDB.HomeSets.URL },
                        ServiceDB.HomeSets.SERVICE_ID + "=?", new String[] { strServiceID }, null, null, null);
                while (cursorHomeSets.moveToNext())
                    info.homeSets.add(cursorHomeSets.getString(0));
            } finally {
                dbHelper.close();
            }

            return info;
        }
    }
}
