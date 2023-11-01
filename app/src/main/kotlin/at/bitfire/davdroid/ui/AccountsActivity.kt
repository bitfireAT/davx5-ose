/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.GravityCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityAccountsBinding
import at.bitfire.davdroid.ui.intro.IntroActivity
import at.bitfire.davdroid.ui.setup.LoginActivity
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountsActivity: AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, SwipeRefreshLayout.OnRefreshListener {

    @Inject lateinit var accountsDrawerHandler: AccountsDrawerHandler

    private lateinit var binding: ActivityAccountsBinding
    val model by viewModels<AccountListModel>()

    private val introActivityLauncher = registerForActivityResult(IntroActivity.Contract) { cancelled ->
        if (cancelled)
            finish()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            // use a separate thread to check whether IntroActivity should be shown
            CoroutineScope(Dispatchers.Default).launch {
                if (IntroActivity.shouldShowIntroActivity(this@AccountsActivity))
                    introActivityLauncher.launch(null)
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

        onBackPressedDispatcher.addCallback(this) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            else
                finish()
        }

        binding.content.swipeRefresh.setOnRefreshListener(this)

        model.feedback.observe(this) { message ->
            if (message != null) {
                Snackbar.make(binding.drawerLayout, message, Snackbar.LENGTH_LONG).show()
                model.feedback.value = null
            }
        }

        // handle "Sync all" intent from launcher shortcut
        if (savedInstanceState == null && intent.action == Intent.ACTION_SYNC)
            model.syncAllAccounts(true)
    }

    override fun onResume() {
        super.onResume()
        accountsDrawerHandler.initMenu(this, binding.navView.menu)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        accountsDrawerHandler.onNavigationItemSelected(this, item)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onRefresh() {
        // Disable swipe-down refresh spinner, as we use the progress bars instead
        binding.content.swipeRefresh.isRefreshing = false

        model.syncAllAccounts()
    }

}