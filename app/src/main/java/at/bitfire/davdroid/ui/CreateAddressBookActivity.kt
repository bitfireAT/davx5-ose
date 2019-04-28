/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityCreateAddressBookBinding
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.model.Service
import org.apache.commons.lang3.StringUtils
import java.util.*
import kotlin.concurrent.thread

class CreateAddressBookActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        model = ViewModelProviders.of(this).get(Model::class.java)
        (intent?.getParcelableExtra(EXTRA_ACCOUNT) as? Account)?.let {
            model.initialize(it)
        }

        val binding = DataBindingUtil.setContentView<ActivityCreateAddressBookBinding>(this, R.layout.activity_create_address_book)
        binding.lifecycleOwner = this
        binding.model = model
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_create_collection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            if (item.itemId == android.R.id.home) {
                val intent = Intent(this, AccountActivity::class.java)
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, model.account)
                NavUtils.navigateUpTo(this, intent)
                true
            } else
                false

    fun onCreateCollection(item: MenuItem) {
        var ok = true

        val args = Bundle()

        val parent = model.homeSets.value?.getItem(model.idxHomeSet.value!!) ?: return
        args.putString(CreateCollectionFragment.ARG_URL, parent.url.resolve(UUID.randomUUID().toString() + "/").toString())

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
    ) : AndroidViewModel(application) {

        var account: Account? = null

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()

        val description = MutableLiveData<String>()

        val homeSets = MutableLiveData<HomeSetAdapter>()
        val idxHomeSet = MutableLiveData<Int>()

        @MainThread
        fun initialize(account: Account) {
            if (this.account != null)
                return
            this.account = account

            thread {
                // load account info
                val adapter = HomeSetAdapter(getApplication())

                val db = AppDatabase.getInstance(getApplication())
                db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)?.let { service ->
                    val homeSets = db.homeSetDao().getByService(service.id)
                    adapter.addAll(homeSets)
                }

                if (!adapter.isEmpty) {
                    homeSets.postValue(adapter)
                    idxHomeSet.postValue(0)
                }
            }
        }

    }

}
