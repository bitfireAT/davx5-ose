/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.webdav

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.method.LinkMovementMethod
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityWebdavMountsBinding
import at.bitfire.davdroid.databinding.WebdavMountsItemBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.UiUtils
import at.bitfire.davdroid.webdav.CredentialsStore
import at.bitfire.davdroid.webdav.DavDocumentsProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.util.logging.Level
import javax.inject.Inject


@AndroidEntryPoint
class WebdavMountsActivity: AppCompatActivity() {

    private lateinit var binding: ActivityWebdavMountsBinding
    private val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWebdavMountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.webdavMountsSeeManual.text = HtmlCompat.fromHtml(getString(R.string.webdav_add_mount_empty_more_info, helpUrl()), 0)
        binding.webdavMountsSeeManual.movementMethod = LinkMovementMethod.getInstance()

        val adapter = MountsAdapter(this, model)
        binding.list.adapter = adapter
        binding.list.layoutManager = LinearLayoutManager(this)
        model.mountInfos.observe(this, Observer { mounts ->
            adapter.submitList(ArrayList(mounts))

            val hasMounts = mounts.isNotEmpty()
            binding.list.visibility = if (hasMounts) View.VISIBLE else View.GONE
            binding.empty.visibility = if (hasMounts) View.GONE else View.VISIBLE
        })

        val browser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(shareIntent, null))
            }
        }
        model.browseIntent.observe(this, Observer { intent ->
            if (intent != null) {
                browser.launch(intent)
            }
        })

        binding.add.setOnClickListener {
            startActivity(Intent(this, AddWebdavMountActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_webdav_mounts, menu)
        return true
    }

    fun onShowHelp(item: MenuItem) {
        UiUtils.launchUri(this, helpUrl())
    }

    private fun helpUrl() =
        App.homepageUrl(this).buildUpon()
            .appendEncodedPath("manual/webdav_mounts.html")
            .build()


    data class MountInfo(
        val mount: WebDavMount,
        val rootDocument: WebDavDocument?
    )

    class MountsAdapter(
        val context: Context,
        val model: Model
    ): ListAdapter<MountInfo, MountsAdapter.ViewHolder>(object: DiffUtil.ItemCallback<MountInfo>() {
        override fun areItemsTheSame(oldItem: MountInfo, newItem: MountInfo) =
            oldItem.mount.id == newItem.mount.id
        override fun areContentsTheSame(oldItem: MountInfo, newItem: MountInfo) =
            oldItem.mount.name == newItem.mount.name && oldItem.mount.url == newItem.mount.url &&
            oldItem.rootDocument?.quotaUsed == newItem.rootDocument?.quotaUsed &&
            oldItem.rootDocument?.quotaAvailable == newItem.rootDocument?.quotaAvailable
    }) {
        class ViewHolder(val binding: WebdavMountsItemBinding): RecyclerView.ViewHolder(binding.root)

        val authority = context.getString(R.string.webdav_authority)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = WebdavMountsItemBinding.inflate(inflater, parent, false)

            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val info = getItem(position)
            val binding = holder.binding
            binding.name.text = info.mount.name
            binding.url.text = info.mount.url.toString()

            val quotaUsed = info.rootDocument?.quotaUsed
            val quotaAvailable = info.rootDocument?.quotaAvailable
            if (quotaUsed != null && quotaAvailable != null) {
                val quotaTotal = quotaUsed + quotaAvailable

                binding.quotaProgress.visibility = View.VISIBLE
                binding.quotaProgress.progress = (quotaUsed*100 / quotaTotal).toInt()

                binding.quota.visibility = View.VISIBLE
                binding.quota.text = context.getString(R.string.webdav_mounts_quota_used_available,
                    FileUtils.byteCountToDisplaySize(quotaUsed),
                    FileUtils.byteCountToDisplaySize(quotaAvailable)
                )
            } else {
                binding.quotaProgress.visibility = View.GONE
                binding.quota.visibility = View.GONE
            }

            binding.browse.setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                val uri = DocumentsContract.buildRootUri(authority, info.mount.id.toString())
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                model.browseIntent.value = intent
                model.browseIntent.value = null
            }

            binding.removeMountpoint.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(R.string.webdav_remove_mount_title)
                    .setMessage(R.string.webdav_remove_mount_text)
                    .setPositiveButton(R.string.dialog_remove) { _, _ ->
                        Logger.log.log(Level.INFO, "User removes mount point", info.mount)
                        model.remove(info.mount)
                    }
                    .setNegativeButton(R.string.dialog_deny, null)
                    .show()
            }
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
        @ApplicationContext val context: Context,
        val db: AppDatabase
    ): ViewModel() {

        val authority = context.getString(R.string.webdav_authority)

        val mountInfos = object: MediatorLiveData<List<MountInfo>>() {
            var mounts: List<WebDavMount>? = null
            var roots: List<WebDavDocument>? = null
            init {
                addSource(db.webDavMountDao().getAllLive()) { newMounts ->
                    mounts = newMounts

                    viewModelScope.launch(Dispatchers.IO) {
                        // query children of root document for every mount to show quota
                        for (mount in newMounts)
                            queryChildrenOfRoot(mount)

                        merge()
                    }
                }
                addSource(db.webDavDocumentDao().getRootsLive()) { newRoots ->
                    roots = newRoots
                    merge()
                }
            }
            @Synchronized
            fun merge() {
                val result = mutableListOf<MountInfo>()
                mounts?.forEach { mount ->
                    result += MountInfo(
                        mount = mount,
                        rootDocument = roots?.firstOrNull { it.mountId == mount.id }
                    )
                }
                postValue(result)
            }
        }

        val browseIntent = MutableLiveData<Intent>()

        /**
         * Removes the mountpoint (deleting connection information)
         */
        fun remove(mount: WebDavMount) {
            viewModelScope.launch(Dispatchers.IO) {
                // remove mount from database
                db.webDavMountDao().delete(mount)

                // remove credentials, too
                CredentialsStore(context).setCredentials(mount.id, null)

                // notify content URI listeners
                DavDocumentsProvider.notifyMountsChanged(context)
            }
        }


        private fun queryChildrenOfRoot(mount: WebDavMount) {
            val resolver = context.contentResolver
            db.webDavDocumentDao().getOrCreateRoot(mount).let { root ->
                resolver.query(DocumentsContract.buildChildDocumentsUri(authority, root.id.toString()), null, null, null, null)?.close()
            }
        }

    }

}