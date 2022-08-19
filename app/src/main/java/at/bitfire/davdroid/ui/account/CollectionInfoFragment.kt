/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.databinding.CollectionPropertiesBinding
import at.bitfire.davdroid.db.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CollectionInfoFragment: DialogFragment() {

    companion object {

        private const val ARGS_COLLECTION_ID = "collectionId"

        fun newInstance(collectionId: Long): CollectionInfoFragment {
            val frag = CollectionInfoFragment()
            val args = Bundle(1)
            args.putLong(ARGS_COLLECTION_ID, collectionId)
            frag.arguments = args
            return frag
        }

    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model>() {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(requireArguments().getLong(ARGS_COLLECTION_ID)) as T
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = CollectionPropertiesBinding.inflate(inflater, container, false)
        view.lifecycleOwner = this
        view.model = model

        return view.root
    }


    class Model @AssistedInject constructor(
        val db: AppDatabase,
        @Assisted collectionId: Long
    ): ViewModel() {

        @AssistedFactory
        interface Factory {
            fun create(collectionId: Long): Model
        }

        var collection = db.collectionDao().getLive(collectionId)

    }

}