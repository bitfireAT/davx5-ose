package at.bitfire.davdroid.ui.account

import android.content.Intent
import android.view.*
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.ui.CreateCalendarActivity
import kotlinx.android.synthetic.main.account_caldav_item.view.*

class CalendarsFragment: CollectionsFragment() {

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
            inflater.inflate(R.menu.caldav_actions, menu)

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

    override fun createAdapter(): CollectionAdapter = CalendarAdapter(accountModel)


    class CalendarViewHolder(
            parent: ViewGroup,
            accountModel: AccountActivity2.Model
    ): CollectionViewHolder(parent, R.layout.account_caldav_item, accountModel) {

        override fun bindTo(item: Collection) {
            super.bindTo(item)

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

            v.action_overflow.setOnClickListener {
                Logger.log.info("Action click")
            }
        }

    }

    class CalendarAdapter(
            accountModel: AccountActivity2.Model
    ): CollectionAdapter(accountModel) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                CalendarViewHolder(parent, accountModel)

    }

}