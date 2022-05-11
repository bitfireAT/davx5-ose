/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.databinding.CollectionPropertiesBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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

    val model by viewModels<Model>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        arguments?.getLong(ARGS_COLLECTION_ID)?.let { id ->
            model.initialize(id)
        }

        val view = CollectionPropertiesBinding.inflate(inflater, container, false)
        view.lifecycleOwner = this
        view.model = model

        return view.root
    }


    class Model(
            application: Application
    ): AndroidViewModel(application), KoinComponent {

        val db by inject<AppDatabase>()
        var collection = MutableLiveData<Collection>()

        private var initialized = false

        @UiThread
        fun initialize(collectionId: Long) {
            // TODO use constructor and model factory instead of custom initialize()
            if (initialized)
                return
            initialized = true

            viewModelScope.launch(Dispatchers.IO) {
                collection.postValue(db.collectionDao().get(collectionId))
            }
        }

    }

}