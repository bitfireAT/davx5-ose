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
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.setup.LoginActivity
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.accounts_content.*
import kotlinx.android.synthetic.main.activity_accounts.*
import kotlinx.android.synthetic.main.activity_accounts.view.*

class AccountsActivity: AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, SyncStatusObserver {

    companion object {
        val accountsDrawerHandler = DefaultAccountsDrawerHandler()

        const val fragTagStartup = "startup"
    }

    private lateinit var settings: Settings

    private var syncStatusSnackbar: Snackbar? = null
    private var syncStatusObserver: Any? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.getInstance(this)

        setContentView(R.layout.activity_accounts)
        setSupportActionBar(toolbar)

        if (supportFragmentManager.findFragmentByTag(fragTagStartup) == null) {
            val ft = supportFragmentManager.beginTransaction()
            StartupDialogFragment.getStartupDialogs(this).forEach { ft.add(it, fragTagStartup) }
            ft.commit()
        }

        fab.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        fab.show()

        accountsDrawerHandler.initMenu(this, drawer_layout.nav_view.menu)
        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        nav_view.itemIconTintList = null
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
                    .setAction(R.string.accounts_global_sync_enable) {
                        ContentResolver.setMasterSyncAutomatically(true)
                    }
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
        val processed = accountsDrawerHandler.onNavigationItemSelected(this, item)
        drawer_layout.closeDrawer(GravityCompat.START)
        return processed
    }

}
