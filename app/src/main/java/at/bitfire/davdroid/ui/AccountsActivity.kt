/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Intent
import android.content.SyncStatusObserver
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.setup.LoginActivity
import kotlinx.android.synthetic.main.accounts_content.*
import kotlinx.android.synthetic.main.activity_accounts.*
import java.util.*

class AccountsActivity: AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, SyncStatusObserver {

    private var syncStatusSnackbar: Snackbar? = null
    private var syncStatusObserver: Any? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        setSupportActionBar(toolbar)

        fab.setOnClickListener({
            startActivity(Intent(this@AccountsActivity, LoginActivity::class.java))
        })

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer_layout.setDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        nav_view.itemIconTintList = null

        if (savedInstanceState == null && packageName != getCallingPackage()) {
            // first call
            val ft = supportFragmentManager.beginTransaction()
            StartupDialogFragment.getStartupDialogs(this).forEach { ft.add(it, null) }
            ft.commit()
        }
    }

    override fun onResume() {
        super.onResume()

        onStatusChanged(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS)
        syncStatusObserver = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
    }

    override fun onPause() {
        super.onPause()
        syncStatusObserver?.let {
            ContentResolver.removeStatusChangeListener(it)
            syncStatusObserver = null
        }
    }

    override fun onStatusChanged(which: Int) {
        syncStatusSnackbar?.let {
            it.dismiss()
            syncStatusSnackbar = null
        }

        if (!ContentResolver.getMasterSyncAutomatically()) {
            val snackbar = Snackbar
                    .make(findViewById(R.id.coordinator), R.string.accounts_global_sync_disabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.accounts_global_sync_enable, {
                            ContentResolver.setMasterSyncAutomatically(true)
                        })
            syncStatusSnackbar = snackbar
            snackbar.show()
        }
    }


    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START))
            drawer_layout.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        var processed = true
        when (item.itemId) {
            R.id.nav_about ->
                startActivity(Intent(this, AboutActivity::class.java))
            R.id.nav_app_settings ->
                startActivity(Intent(this, AppSettingsActivity::class.java))
            R.id.nav_twitter ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/davdroidapp")))
            R.id.nav_website ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.homepage_url))))
            R.id.nav_faq ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.navigation_drawer_faq_url))))
            R.id.nav_forums ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.homepage_url))
                        .buildUpon().appendEncodedPath("forums/").build()))
            R.id.nav_donate ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.homepage_url))
                        .buildUpon().appendEncodedPath("donate/").build()))
            else ->
                processed = false
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return processed
    }

}
