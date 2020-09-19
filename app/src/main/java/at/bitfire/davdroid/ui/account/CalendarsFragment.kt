package at.bitfire.davdroid.ui.account

import android.content.Intent
import android.view.*
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.ui.CreateCalendarActivity
import kotlinx.android.synthetic.main.account_caldav_item.view.*
import kotlinx.android.synthetic.main.account_collections.*

class CalendarsFragment: CollectionsFragment() {

    override val noCollectionsStringId = R.string.account_no_calendars

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
            inflater.inflate(R.menu.caldav_actions, menu)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item))
            return true

        if (item.itemId == R.id.create) {
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
            permissionsCard.visibility = View.GONE
        else {
            permissionsText.setText(when {
                !calendarPermissions && tasksPermissions -> R.string.account_caldav_missing_calendar_permissions
                calendarPermissions && !tasksPermissions -> R.string.account_caldav_missing_tasks_permissions
                else -> R.string.account_caldav_missing_permissions
            })
            permissionsCard.visibility = View.VISIBLE
        }
    }
    override fun createAdapter(): CollectionAdapter = CalendarAdapter(accountModel)


    class CalendarViewHolder(
            parent: ViewGroup,
            accountModel: AccountActivity.Model
    ): CollectionViewHolder(parent, R.layout.account_caldav_item, accountModel) {

        override fun bindTo(item: Collection) {
            val v = itemView
            v.color.setBackgroundColor(item.color ?: Constants.DAVDROID_GREEN_RGBA)

            v.sync.isChecked = item.sync
            v.title.text = item.title()

            if (item.description.isNullOrBlank())
                v.description.visibility = View.GONE
            else {
                v.description.text = item.description
                v.description.visibility = View.VISIBLE
            }

            v.read_only.visibility = if (item.readOnly()) View.VISIBLE else View.GONE
            v.events.visibility = if (item.supportsVEVENT == true) View.VISIBLE else View.GONE
            v.tasks.visibility = if (item.supportsVTODO == true) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                accountModel.toggleSync(item)
            }
            v.action_overflow.setOnClickListener(CollectionPopupListener(accountModel, item))
        }

    }

    class CalendarAdapter(
            accountModel: AccountActivity.Model
    ): CollectionAdapter(accountModel) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                CalendarViewHolder(parent, accountModel)

    }

}