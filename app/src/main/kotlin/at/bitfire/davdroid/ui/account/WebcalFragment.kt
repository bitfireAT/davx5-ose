/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.AccountCaldavItemBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.WebcalSubscription
import at.bitfire.davdroid.syncadapter.WebcalSyncWorker
import at.bitfire.davdroid.util.PermissionUtils
import com.google.android.material.snackbar.Snackbar
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WebcalFragment: CollectionsFragment() {

    override val noCollectionsStringId = R.string.account_no_webcals

    @Inject lateinit var webcalModelFactory: WebcalModel.Factory
    private val webcalModel by viewModels<WebcalModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                webcalModelFactory.create(
                    requireArguments().getLong(EXTRA_SERVICE_ID)
                ) as T
        }
    }

    private val menuProvider = object : CollectionsMenuProvider() {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.caldav_actions, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            menu.findItem(R.id.create_calendar).isVisible = false
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().addMenuProvider(menuProvider)
    }

    override fun onPause() {
        super.onPause()
        requireActivity().removeMenuProvider(menuProvider)
    }


    override fun checkPermissions() {
        if (PermissionUtils.havePermissions(requireActivity(), PermissionUtils.CALENDAR_PERMISSIONS))
            binding.permissionsCard.visibility = View.GONE
        else {
            binding.permissionsText.setText(R.string.account_webcal_missing_calendar_permissions)
            binding.permissionsCard.visibility = View.VISIBLE
        }
    }

    override fun createAdapter(): CollectionAdapter = WebcalAdapter(accountModel, webcalModel, this)


    class CalendarViewHolder(
        private val parent: ViewGroup,
        accountModel: AccountActivity.Model,
        private val webcalModel: WebcalModel,
        private val webcalFragment: WebcalFragment
    ): CollectionViewHolder<AccountCaldavItemBinding>(parent, AccountCaldavItemBinding.inflate(LayoutInflater.from(parent.context), parent, false), accountModel) {

        override fun bindTo(item: Collection) {
            binding.color.setBackgroundColor(item.color ?: Constants.DAVDROID_GREEN_RGBA)

            binding.sync.isChecked = item.sync
            binding.title.text = item.title()

            if (item.description.isNullOrBlank())
                binding.description.visibility = View.GONE
            else {
                binding.description.text = item.description
                binding.description.visibility = View.VISIBLE
            }

            binding.readOnly.visibility = View.VISIBLE
            binding.events.visibility = if (item.supportsVEVENT == true) View.VISIBLE else View.GONE
            binding.tasks.visibility = if (item.supportsVTODO == true) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                if (item.sync)
                    webcalModel.unsubscribe(item)
                else
                    subscribe(item)
            }
            binding.actionOverflow.setOnClickListener(CollectionPopupListener(accountModel, item, webcalFragment.parentFragmentManager))
        }

        private fun subscribe(item: Collection) {
            AlertDialog.Builder(webcalFragment.requireActivity())
                .setItems(R.array.webcal_subscribe_directly_or_external) { _, btn ->
                    when (btn) {
                        0 -> webcalModel.subscribe(item)
                        1 -> subscribeExternal(item)
                    }
                }
                .show()
        }

        private fun subscribeExternal(item: Collection) {
            var uri = Uri.parse(item.source.toString())
            when {
                uri.scheme.equals("http", true) -> uri = uri.buildUpon().scheme("webcal").build()
                uri.scheme.equals("https", true) -> uri = uri.buildUpon().scheme("webcals").build()
            }

            val intent = Intent(Intent.ACTION_VIEW, uri)
            item.displayName?.let { intent.putExtra("title", it) }
            item.color?.let { intent.putExtra("color", it) }

            val activity = webcalFragment.requireActivity()
            if (activity.packageManager.resolveActivity(intent, 0) != null)
                activity.startActivity(intent)
            else {
                val snack = Snackbar.make(parent, R.string.account_no_webcal_handler_found, Snackbar.LENGTH_LONG)

                val installIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=at.bitfire.icsdroid"))
                if (activity.packageManager.resolveActivity(installIntent, 0) != null)
                    snack.setAction(R.string.account_install_icsx5) {
                        activity.startActivityForResult(installIntent, 0)
                    }

                snack.show()
            }
        }

    }

    class WebcalAdapter(
        accountModel: AccountActivity.Model,
        private val webcalModel: WebcalModel,
        val webcalFragment: WebcalFragment
    ): CollectionAdapter(accountModel) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CalendarViewHolder(parent, accountModel, webcalModel, webcalFragment)

    }


    class WebcalModel @AssistedInject constructor(
        application: Application,
        val db: AppDatabase,
        @Assisted val serviceId: Long
    ): AndroidViewModel(application) {

        @AssistedFactory
        interface Factory {
            fun create(serviceId: Long): WebcalModel
        }

        private val dao = db.webcalSubscriptionDao()

        fun subscribe(item: Collection) = viewModelScope.launch(Dispatchers.IO) {
            val subscription = WebcalSubscription.fromCollection(item)
            dao.insertAndUpdateCollection(db.collectionDao(), subscription)
            WebcalSyncWorker.updateWorker(getApplication(), db)
        }

        fun unsubscribe(item: Collection) = viewModelScope.launch(Dispatchers.IO) {
            dao.getByCollectionId(item.id)?.let { subscription ->
                dao.delete(subscription)
                db.collectionDao().updateSync(item.id, false)
            }
            WebcalSyncWorker.updateWorker(getApplication(), db)
        }

    }

}