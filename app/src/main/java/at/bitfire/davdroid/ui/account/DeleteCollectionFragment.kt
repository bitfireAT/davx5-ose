/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import at.bitfire.dav4jvm.DavResource
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.databinding.DeleteCollectionBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.ExceptionInfoFragment
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
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

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model>() {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelFactory.create(
                    requireArguments().getParcelable(ARG_ACCOUNT) ?: throw IllegalArgumentException("ARG_ACCOUNT required"),
                    requireArguments().getLong(ARG_COLLECTION_ID)
                ) as T
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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


    class Model @AssistedInject constructor(
        @ApplicationContext val context: Context,
        val db: AppDatabase,
        @Assisted var account: Account,
        @Assisted val collectionId: Long
    ): ViewModel() {

        @AssistedFactory
        interface Factory {
            fun create(account: Account, collectionId: Long): Model
        }

        var collectionInfo: Collection? = null

        val confirmation = MutableLiveData<Boolean>()
        val result = MutableLiveData<Exception>()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                collectionInfo = db.collectionDao().get(collectionId)
            }
        }

        fun deleteCollection(): LiveData<Exception> {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                val collectionInfo = collectionInfo ?: return@launch

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