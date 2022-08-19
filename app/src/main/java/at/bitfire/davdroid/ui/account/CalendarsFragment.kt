/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.content.Intent
import android.view.*
import androidx.fragment.app.FragmentManager
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.AccountCaldavItemBinding
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.TaskUtils

class CalendarsFragment: CollectionsFragment() {

    override val noCollectionsStringId = R.string.account_no_calendars

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
            inflater.inflate(R.menu.caldav_actions, menu)

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.create_calendar).isVisible = model.hasWriteableCollections.value ?: false
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item))
            return true

        if (item.itemId == R.id.create_calendar) {
            val intent = Intent(requireActivity(), CreateCalendarActivity::class.java)
            intent.putExtra(CreateCalendarActivity.EXTRA_ACCOUNT, accountModel.account)
            startActivity(intent)
            return true
        }

        return false
    }


    override fun checkPermissions() {
        val calendarPermissions = PermissionUtils.havePermissions(requireActivity(), PermissionUtils.CALENDAR_PERMISSIONS)
        val taskProvider = TaskUtils.currentProvider(requireActivity())
        val tasksPermissions = taskProvider == null ||                                         // no task provider OR
                PermissionUtils.havePermissions(requireActivity(), taskProvider.permissions)   // task permissions granted
        if (calendarPermissions && tasksPermissions)
            binding.permissionsCard.visibility = View.GONE
        else {
            binding.permissionsText.setText(when {
                !calendarPermissions && tasksPermissions -> R.string.account_caldav_missing_calendar_permissions
                calendarPermissions && !tasksPermissions -> R.string.account_caldav_missing_tasks_permissions
                else -> R.string.account_caldav_missing_permissions
            })
            binding.permissionsCard.visibility = View.VISIBLE
        }
    }
    override fun createAdapter(): CollectionAdapter = CalendarAdapter(accountModel, parentFragmentManager)


    class CalendarViewHolder(
        parent: ViewGroup,
        accountModel: AccountActivity.Model,
        val fragmentManager: FragmentManager
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

            binding.readOnly.visibility = if (item.readOnly()) View.VISIBLE else View.GONE
            binding.events.visibility = if (item.supportsVEVENT == true) View.VISIBLE else View.GONE
            binding.tasks.visibility = if (item.supportsVTODO == true) View.VISIBLE else View.GONE
            binding.journals.visibility = if (item.supportsVJOURNAL == true) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                accountModel.toggleSync(item)
            }
            binding.actionOverflow.setOnClickListener(CollectionPopupListener(accountModel, item, fragmentManager))
        }

    }

    class CalendarAdapter(
        accountModel: AccountActivity.Model,
        val fragmentManager: FragmentManager
    ): CollectionAdapter(accountModel) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                CalendarViewHolder(parent, accountModel, fragmentManager)

    }

}