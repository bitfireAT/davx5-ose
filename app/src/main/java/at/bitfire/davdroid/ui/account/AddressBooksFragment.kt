package at.bitfire.davdroid.ui.account

import android.content.Intent
import android.view.*
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.ui.CollectionInfoFragment
import at.bitfire.davdroid.ui.CreateAddressBookActivity
import at.bitfire.davdroid.ui.DeleteCollectionFragment
import kotlinx.android.synthetic.main.account_carddav_item.view.*

class AddressBooksFragment: CollectionsFragment() {

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
            inflater.inflate(R.menu.carddav_actions, menu)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item))
            return true

        if (item.itemId == R.id.create_address_book) {
            val intent = Intent(requireActivity(), CreateAddressBookActivity::class.java)
            intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, accountModel.account)
            startActivity(intent)
            return true
        }

        return false
    }

    override fun createAdapter(): CollectionAdapter<*> =
            AddressBookAdapter(accountModel)


    class AddressBookViewHolder(
            parent: ViewGroup,
            accountModel: AccountActivity2.Model
    ): CollectionViewHolder(parent, R.layout.account_carddav_item, accountModel) {

        private val fragmentManager = (parent.context as AppCompatActivity).supportFragmentManager

        override fun bindTo(item: Collection) {
            super.bindTo(item)

            val v = itemView
            v.sync.isChecked = item.sync
            v.title.text = item.title()

            if (item.description.isNullOrBlank())
                v.description.visibility = View.GONE
            else {
                v.description.text = item.description
                v.description.visibility = View.VISIBLE
            }

            v.read_only.visibility = if (item.readOnly()) View.VISIBLE else View.GONE

            v.action_overflow.setOnClickListener { anchor ->
                val popup = PopupMenu(v.context, anchor, Gravity.RIGHT)
                popup.inflate(R.menu.account_collection_operations)

                with(popup.menu.findItem(R.id.force_read_only)) {
                    if (item.privWriteContent)
                        isChecked = item.forceReadOnly
                    else
                        isVisible = false
                }
                popup.menu.findItem(R.id.delete_collection).isVisible = item.privUnbind

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.force_read_only -> {
                            accountModel.toggleReadOnly(item)
                        }
                        R.id.properties ->
                            CollectionInfoFragment.newInstance(item.id).show(fragmentManager, null)
                        R.id.delete_collection ->
                            DeleteCollectionFragment.newInstance(accountModel.account, item.id).show(fragmentManager, null)
                    }
                    true
                }
                popup.show()
            }
        }

    }

    class AddressBookAdapter(
            accountModel: AccountActivity2.Model
    ): CollectionAdapter<AddressBookViewHolder>(accountModel) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                AddressBookViewHolder(parent, accountModel)

    }

}