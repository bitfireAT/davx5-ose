/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.accounts.AccountManager
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncStatusObserver
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.AnyThread
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.GravityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityAccountsBinding
import at.bitfire.davdroid.ui.intro.IntroActivity
import at.bitfire.davdroid.ui.setup.LoginActivity
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.infomaniak.lib.login.InfomaniakLogin
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountsActivity: AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        const val REQUEST_INTRO = 0
        private const val WEB_VIEW_LOGIN_REQ = 1
    }

    @Inject lateinit var accountsDrawerHandler: AccountsDrawerHandler

    private lateinit var binding: ActivityAccountsBinding
    private val model by viewModels<Model>()

    private var syncStatusSnackbar: Snackbar? = null

    private lateinit var infomaniakLogin: InfomaniakLogin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            CoroutineScope(Dispatchers.Default).launch {
                // use a separate thread to check whether IntroActivity should be shown
                if (IntroActivity.shouldShowIntroActivity(this@AccountsActivity)) {
                    val intro = Intent(this@AccountsActivity, IntroActivity::class.java)
                    startActivityForResult(intro, REQUEST_INTRO)
                }
            }
        }

        binding = ActivityAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // kSync
        infomaniakLogin = InfomaniakLogin(
                context = this,
                clientID = BuildConfig.CLIENT_ID,
                appUID = BuildConfig.APPLICATION_ID
        )

        binding.content.fab.setOnClickListener {
//            startActivity(Intent(this, LoginActivity::class.java))
            infomaniakLogin.startWebViewLogin(WEB_VIEW_LOGIN_REQ)
        }
        binding.content.fab.show()

        model.showSyncDisabled.observe(this) { syncDisabled ->
            if (syncDisabled) {
                val snackbar = Snackbar
                    .make(binding.content.coordinator, R.string.accounts_global_sync_disabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.accounts_global_sync_enable) {
                        ContentResolver.setMasterSyncAutomatically(true)
                    }
                snackbar.show()
                syncStatusSnackbar = snackbar
            } else {
                syncStatusSnackbar?.let { snackbar ->
                    snackbar.dismiss()
                    syncStatusSnackbar = null
                }
            }
        }

        setSupportActionBar(binding.content.toolbar)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.content.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)
        binding.navView.itemIconTintList = null

        // handle "Sync all" intent from launcher shortcut
        if (savedInstanceState == null && intent.action == Intent.ACTION_SYNC)
            syncAllAccounts()
    }

    override fun onResume() {
        super.onResume()
        accountsDrawerHandler.initMenu(this, binding.navView.menu)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_INTRO && resultCode == Activity.RESULT_CANCELED)
            finish()
        else if (requestCode == WEB_VIEW_LOGIN_REQ && resultCode == RESULT_OK) {
            val code = data?.extras?.getString(InfomaniakLogin.CODE_TAG)
            if (code.isNullOrBlank()) {
                val translatedError = data?.extras?.getString(InfomaniakLogin.ERROR_TRANSLATED_TAG)
                Toast.makeText(this, translatedError, Toast.LENGTH_LONG).show()
            } else {
                Intent(this, LoginActivity::class.java).apply {
                    putExtra("code", code)
                    startActivity(this)
                }
            }
        }
        else
            super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        accountsDrawerHandler.onNavigationItemSelected(this, item)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }


    private fun allAccounts() =
        AccountManager.get(this).getAccountsByType(getString(R.string.account_type))

    fun syncAllAccounts(item: MenuItem? = null) {
        if (Build.VERSION.SDK_INT >= 25)
            getSystemService<ShortcutManager>()?.reportShortcutUsed(UiUtils.SHORTCUT_SYNC_ALL)

        val accounts = allAccounts()
        for (account in accounts)
            DavUtils.requestSync(this, account)
    }


    @HiltViewModel
    class Model @Inject constructor(
        @ApplicationContext val context: Context
    ): ViewModel(), SyncStatusObserver {

        private var syncStatusObserver: Any? = null
        val showSyncDisabled = MutableLiveData(false)

        init {
            syncStatusObserver = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
            onStatusChanged(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS)
        }

        override fun onCleared() {
            ContentResolver.removeStatusChangeListener(syncStatusObserver)
        }

        @AnyThread
        override fun onStatusChanged(which: Int) {
            showSyncDisabled.postValue(!ContentResolver.getMasterSyncAutomatically())
        }

    }

}
