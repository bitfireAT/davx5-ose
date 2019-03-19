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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import at.bitfire.dav4jvm.DavResource
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.databinding.DeleteCollectionBinding
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.settings.AccountSettings
import kotlin.concurrent.thread

class DeleteCollectionFragment: DialogFragment() {

    companion object {
        const val ARG_ACCOUNT = "account"
        const val ARG_COLLECTION_INFO = "collectionInfo"

        fun newInstance(account: Account, collectionInfo: CollectionInfo): DialogFragment {
            val frag = DeleteCollectionFragment()
            val args = Bundle(2)
            args.putParcelable(ARG_ACCOUNT, account)
            args.putParcelable(ARG_COLLECTION_INFO, collectionInfo)
            frag.arguments = args
            return frag
        }
    }

    private lateinit var model: DeleteCollectionModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProviders.of(this).get(DeleteCollectionModel::class.java)

        model.account = arguments?.getParcelable(ARG_ACCOUNT) as? Account
        model.collectionInfo = arguments?.getParcelable(ARG_COLLECTION_INFO) as? CollectionInfo
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DeleteCollectionBinding.inflate(layoutInflater, null, false)
        binding.lifecycleOwner = this
        binding.model = model

        binding.ok.setOnClickListener {
            isCancelable = false
            binding.progress.visibility = View.VISIBLE
            binding.controls.visibility = View.GONE

            model.deleteCollection().observe(this, Observer { exception ->
                if (exception == null)
                    // reload collection list
                    (activity as? AccountActivity)?.reload()
                else
                    requireFragmentManager().beginTransaction()
                            .add(ExceptionInfoFragment.newInstance(exception, model.account), null)
                            .commit()
                dismiss()
            })
        }

        binding.cancel.setOnClickListener {
            dismiss()
        }

        return binding.root
    }


    class DeleteCollectionModel(
            application: Application
    ): AndroidViewModel(application) {

        var account: Account? = null
        var collectionInfo: CollectionInfo? = null

        val confirmation = MutableLiveData<Boolean>()
        val result = MutableLiveData<Exception>()

        fun deleteCollection(): LiveData<Exception> {
            thread {
                val account = requireNotNull(account)
                val collectionInfo = requireNotNull(collectionInfo)

                val context = getApplication<Application>()
                HttpClient.Builder(context, AccountSettings(context, account))
                        .setForeground(true)
                        .build().use { httpClient ->
                            try {
                                val collection = DavResource(httpClient.okHttpClient, collectionInfo.url)

                                // delete collection from server
                                collection.delete(null) {}

                                // delete collection locally
                                ServiceDB.OpenHelper(context).use { dbHelper ->
                                    val db = dbHelper.writableDatabase
                                    db.delete(ServiceDB.Collections._TABLE, "${ServiceDB.Collections.ID}=?", arrayOf(collectionInfo.id.toString()))
                                }

                                // return success
                                result.postValue(null)

                            } catch(e: Exception) {
                                // return error
                                result.postValue(e)
                            }
                        }
            }
            return result
        }

    }

}
