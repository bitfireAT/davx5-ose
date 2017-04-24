/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.setup.LoginActivity;

import static android.content.ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS;

public class AccountsActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, SyncStatusObserver {

    private Snackbar syncStatusSnackbar;
    private Object syncStatusObserver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accounts);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AccountsActivity.this, LoginActivity.class));
            }
        });

        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView)findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setItemIconTintList(null);

        if (savedInstanceState == null && !getPackageName().equals(getCallingPackage())) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            for (StartupDialogFragment fragment : StartupDialogFragment.getStartupDialogs(this))
                ft.add(fragment, null);
            ft.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        onStatusChanged(SYNC_OBSERVER_TYPE_SETTINGS);
        syncStatusObserver = ContentResolver.addStatusChangeListener(SYNC_OBSERVER_TYPE_SETTINGS, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (syncStatusObserver != null) {
            ContentResolver.removeStatusChangeListener(syncStatusObserver);
            syncStatusObserver = null;
        }
    }

    @Override
    public void onStatusChanged(int which) {
        if (syncStatusSnackbar != null) {
            syncStatusSnackbar.dismiss();
            syncStatusSnackbar = null;
        }

        if (!ContentResolver.getMasterSyncAutomatically()) {
            syncStatusSnackbar = Snackbar.make(findViewById(R.id.coordinator), R.string.accounts_global_sync_disabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.accounts_global_sync_enable, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ContentResolver.setMasterSyncAutomatically(true);
                        }
                    });
            syncStatusSnackbar.show();
        }
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START);
        else
            super.onBackPressed();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_about:
                startActivity(new Intent(this, AboutActivity.class));
                break;
            case R.id.nav_app_settings:
                startActivity(new Intent(this, AppSettingsActivity.class));
                break;
            case R.id.nav_twitter:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/davdroidapp")));
                break;
            case R.id.nav_website:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.homepage_url))));
                break;
            case R.id.nav_faq:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.navigation_drawer_faq_url))));
                break;
            case R.id.nav_forums:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.homepage_url))
                        .buildUpon().appendEncodedPath("forums/").build()));
                break;
            case R.id.nav_donate:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.homepage_url))
                        .buildUpon().appendEncodedPath("donate/").build()));
                break;
        }

        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


}
