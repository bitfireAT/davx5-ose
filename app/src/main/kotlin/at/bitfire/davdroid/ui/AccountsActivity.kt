/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.accounts.AccountManager
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.getSystemService
import androidx.core.view.GravityCompat
import androidx.lifecycle.AndroidViewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityAccountsBinding
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.davdroid.ui.intro.IntroActivity
import at.bitfire.davdroid.ui.setup.LoginActivity
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountsActivity: AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        const val REQUEST_INTRO = 0
    }

    @Inject lateinit var accountsDrawerHandler: AccountsDrawerHandler

    private lateinit var binding: ActivityAccountsBinding
    val model by viewModels<Model>()


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

        TooltipCompat.setTooltipText(binding.content.fab, binding.content.fab.contentDescription)
        binding.content.fab.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        binding.content.fab.show()

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

        // Notify user that sync will get enqueued if we're not connected to the internet
        model.networkAvailable.value?.let { networkAvailable ->
            if (!networkAvailable)
                Snackbar.make(binding.drawerLayout, R.string.no_internet_sync_scheduled, Snackbar.LENGTH_LONG).show()
        }

        // Enqueue sync worker for all accounts and authorities. Will sync once internet is available
        val accounts = allAccounts()
        for (account in accounts)
            SyncWorker.enqueueAllAuthorities(this, account)
    }


    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        val settings: SettingsManager,
        warnings: AppWarningsManager
    ): AndroidViewModel(application) {

        val networkAvailable = warnings.networkAvailable

    }

}
