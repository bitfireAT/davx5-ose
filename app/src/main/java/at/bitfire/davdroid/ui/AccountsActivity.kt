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
import android.content.Context
import android.content.Intent
import android.content.SyncStatusObserver
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.ui.setup.LoginActivity
import kotlinx.android.synthetic.main.accounts_content.*
import kotlinx.android.synthetic.main.activity_accounts.*

class AccountsActivity: AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, LoaderManager.LoaderCallbacks<AccountsActivity.Settings>, SyncStatusObserver {

    companion object {
        val accountsDrawerHandler = DefaultAccountsDrawerHandler()

        private const val fragTagStartup = "startup"
    }

    private var syncStatusSnackbar: Snackbar? = null
    private var syncStatusObserver: Any? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.setDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        nav_view.itemIconTintList = null

        /* When the DAVdroid main activity is started, start a Settings service that stays in memory
        for better performance. The service stops itself when memory is trimmed. */
        val settingsIntent = Intent(this, Settings::class.java)
        startService(settingsIntent)

        val args = Bundle(1)
        supportLoaderManager.initLoader(0, args, this)
    }

    override fun onCreateLoader(code: Int, args: Bundle?) =
            SettingsLoader(this)

    override fun onLoadFinished(loader: Loader<Settings>, result: Settings?) {
        val result = result ?: return

        if (supportFragmentManager.findFragmentByTag(fragTagStartup) == null) {
            val ft = supportFragmentManager.beginTransaction()
            StartupDialogFragment.getStartupDialogs(this, result.settings).forEach { ft.add(it, fragTagStartup) }
            ft.commit()
        }

        nav_view?.menu?.let {
            accountsDrawerHandler.onSettingsChanged(result.settings, it)
        }
    }

    override fun onLoaderReset(loader: Loader<Settings>) {
        nav_view?.menu?.let {
            accountsDrawerHandler.onSettingsChanged(null, it)
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


    class Settings(
            val settings: ISettings
    )

    class SettingsLoader(
            context: Context
    ): at.bitfire.davdroid.ui.SettingsLoader<Settings>(context) {

        override fun loadInBackground(): Settings? {
            settings?.let {
                val accountManager = AccountManager.get(context)
                val accounts = accountManager.getAccountsByType(context.getString(R.string.account_type))

                return Settings(
                        it
                )
            }
            return null
        }

    }

}
