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
import android.widget.SpinnerAdapter
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityCreateAddressBookBinding
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import okhttp3.HttpUrl
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

        val parent = model.homeSets.value?.getItem(model.idxHomeSet.value!!) as String? ?: return
        HttpUrl.parse(parent)?.let { parentUrl ->
            val info = CollectionInfo(parentUrl.resolve(UUID.randomUUID().toString() + "/")!!)

            val displayName = model.displayName.value
            if (displayName.isNullOrBlank()) {
                model.displayNameError.value = getString(R.string.create_collection_display_name_required)
                ok = false
            } else {
                info.displayName = displayName
                model.displayNameError.value = null
            }

            info.description = StringUtils.trimToNull(model.description.value)

            if (ok) {
                info.type = CollectionInfo.Type.ADDRESS_BOOK
                CreateCollectionFragment.newInstance(model.account!!, info).show(supportFragmentManager, null)
            }
        }
    }


    class Model(
            application: Application
    ) : AndroidViewModel(application) {

        var account: Account? = null

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()

        val description = MutableLiveData<String>()

        val homeSets = MutableLiveData<SpinnerAdapter>()
        val idxHomeSet = MutableLiveData<Int>()

        @MainThread
        fun initialize(account: Account) {
            if (this.account != null)
                return
            this.account = account

            thread {
                // load account info
                ServiceDB.OpenHelper(getApplication()).use { dbHelper ->
                    val adapter = HomesetAdapter(getApplication())
                    val db = dbHelper.readableDatabase
                    db.query(ServiceDB.Services._TABLE, arrayOf(ServiceDB.Services.ID),
                            "${ServiceDB.Services.ACCOUNT_NAME}=? AND ${ServiceDB.Services.SERVICE}=?",
                            arrayOf(account.name, ServiceDB.Services.SERVICE_CARDDAV), null, null, null).use { cursor ->
                        if (cursor.moveToNext()) {
                            val strServiceID = cursor.getString(0)
                            db.query(ServiceDB.HomeSets._TABLE, arrayOf(ServiceDB.HomeSets.URL),
                                    "${ServiceDB.HomeSets.SERVICE_ID}=?", arrayOf(strServiceID), null, null, null).use { c ->
                                while (c.moveToNext())
                                    adapter.add(c.getString(0))
                            }
                        }
                    }
                    if (!adapter.isEmpty) {
                        homeSets.postValue(adapter)
                        idxHomeSet.postValue(0)
                    }
                }
            }
        }

    }

}
