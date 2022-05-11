/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityCreateAddressBookBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.ui.HomeSetAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class CreateAddressBookActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private val account by lazy { intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set") }
    val model by viewModels<Model>()

    lateinit var binding: ActivityCreateAddressBookBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_create_address_book)
        binding.lifecycleOwner = this
        binding.model = model

        val homeSetAdapter = HomeSetAdapter(this)
        model.homeSets.observe(this) { homeSets ->
            homeSetAdapter.clear()
            if (homeSets.isNotEmpty()) {
                homeSetAdapter.addAll(homeSets)
                val firstHomeSet = homeSets.first()
                binding.homeset.setText(firstHomeSet.url.toString(), false)
                model.homeSet = firstHomeSet
            }
        }
        binding.homeset.setAdapter(homeSetAdapter)
        binding.homeset.setOnItemClickListener { parent, view, position, id ->
            model.homeSet = parent.getItemAtPosition(position) as HomeSet?
        }

        if (savedInstanceState == null)
            model.initialize(account)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_create_collection, menu)
        return true
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }


    fun onCreateCollection(item: MenuItem) {
        var ok = true

        val args = Bundle()
        args.putString(CreateCollectionFragment.ARG_SERVICE_TYPE, Service.TYPE_CARDDAV)

        val parent = model.homeSet
        if (parent != null) {
            binding.homesetLayout.error = null
            args.putString(CreateCollectionFragment.ARG_URL, parent.url.resolve(UUID.randomUUID().toString() + "/").toString())
        } else {
            binding.homesetLayout.error = getString(R.string.create_collection_home_set_required)
            ok = false
        }

        val displayName = model.displayName.value
        if (displayName.isNullOrBlank()) {
            model.displayNameError.value = getString(R.string.create_collection_display_name_required)
            ok = false
        } else {
            args.putString(CreateCollectionFragment.ARG_DISPLAY_NAME, displayName)
            model.displayNameError.value = null
        }

        StringUtils.trimToNull(model.description.value)?.let {
            args.putString(CreateCollectionFragment.ARG_DESCRIPTION, it)
        }

        if (ok) {
            args.putParcelable(CreateCollectionFragment.ARG_ACCOUNT, model.account)
            args.putString(CreateCollectionFragment.ARG_TYPE, Collection.TYPE_ADDRESSBOOK)
            val frag = CreateCollectionFragment()
            frag.arguments = args
            frag.show(supportFragmentManager, null)
        }
    }


    class Model(
            application: Application
    ) : AndroidViewModel(application), KoinComponent {

        var account: Account? = null
        val db by inject<AppDatabase>()

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()

        val description = MutableLiveData<String>()

        val homeSets = MutableLiveData<List<HomeSet>>()
        var homeSet: HomeSet? = null

        fun clearNameError(s: Editable) {
            displayNameError.value = null
        }

        @MainThread
        fun initialize(account: Account) {
            // TODO use constructor and model factory instead of custom initialize()
            if (this.account != null)
                return
            this.account = account

            viewModelScope.launch(Dispatchers.IO) {
                // load account info
                db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)?.let { service ->
                    homeSets.postValue(db.homeSetDao().getBindableByService(service.id))
                }
            }
        }

    }

}
