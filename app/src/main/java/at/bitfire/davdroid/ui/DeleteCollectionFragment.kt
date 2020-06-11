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
import androidx.annotation.MainThread
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import at.bitfire.dav4jvm.DavResource
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.databinding.DeleteCollectionBinding
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.settings.AccountSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

class DeleteCollectionFragment: DialogFragment() {

    companion object {
        const val ARG_ACCOUNT = "account"
        const val ARG_COLLECTION_ID = "collectionId"

        fun newInstance(account: Account, collectionId: Long): DialogFragment {
            val frag = DeleteCollectionFragment()
            val args = Bundle(2)
            args.putParcelable(ARG_ACCOUNT, account)
            args.putLong(ARG_COLLECTION_ID, collectionId)
            frag.arguments = args
            return frag
        }
    }

    val model by viewModels<DeleteCollectionModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.initialize(
                requireArguments().getParcelable(ARG_ACCOUNT)!!,
                requireArguments().getLong(ARG_COLLECTION_ID)
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DeleteCollectionBinding.inflate(layoutInflater, null, false)
        binding.lifecycleOwner = this
        binding.model = model

        binding.ok.setOnClickListener {
            isCancelable = false
            binding.progress.visibility = View.VISIBLE
            binding.controls.visibility = View.GONE

            model.deleteCollection().observe(viewLifecycleOwner, Observer { exception ->
                if (exception != null)
                    parentFragmentManager.beginTransaction()
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
        var collectionInfo: Collection? = null

        val db = AppDatabase.getInstance(application)

        val confirmation = MutableLiveData<Boolean>()
        val result = MutableLiveData<Exception>()

        @MainThread
        fun initialize(account: Account, collectionId: Long) {
            if (this.account == null)
                this.account = account

            if (collectionInfo == null)
                viewModelScope.launch(Dispatchers.IO) {
                    collectionInfo = db.collectionDao().get(collectionId)
                }
        }

        fun deleteCollection(): LiveData<Exception> {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                val account = account ?: return@launch
                val collectionInfo = collectionInfo ?: return@launch

                val context = getApplication<Application>()
                HttpClient.Builder(context, AccountSettings(context, account))
                        .setForeground(true)
                        .build().use { httpClient ->
                            try {
                                val collection = DavResource(httpClient.okHttpClient, collectionInfo.url)

                                // delete collection from server
                                collection.delete(null) {}

                                // delete collection locally
                                db.collectionDao().delete(collectionInfo)

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
