/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityCreateAddressBookBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.ui.HomeSetAdapter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class CreateAddressBookActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model>() {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val account = intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
                return modelFactory.create(account) as T
            }
        }
    }

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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_create_collection, menu)
        return true
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, model.account)
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


    class Model @AssistedInject constructor(
        val db: AppDatabase,
        @Assisted val account: Account
    ) : ViewModel() {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()

        val description = MutableLiveData<String>()

        val homeSets = MutableLiveData<List<HomeSet>>()
        var homeSet: HomeSet? = null

        init {
            viewModelScope.launch(Dispatchers.IO) {
                // load account info
                db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)?.let { service ->
                    homeSets.postValue(db.homeSetDao().getBindableByService(service.id))
                }
            }
        }

        fun clearNameError(s: Editable) {
            displayNameError.value = null
        }

    }

}
