package at.bitfire.davdroid.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.PackageChangedReceiver
import at.bitfire.davdroid.PermissionUtils.CALENDAR_PERMISSIONS
import at.bitfire.davdroid.PermissionUtils.CONTACT_PERMSSIONS
import at.bitfire.davdroid.PermissionUtils.TASKS_PERMISSIONS
import at.bitfire.davdroid.PermissionUtils.havePermissions
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityPermissionsBinding
import at.bitfire.davdroid.resource.LocalTaskList

class PermissionsFragment: Fragment() {

    lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this).get(Model::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ActivityPermissionsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.model = model

        binding.text.text = getString(R.string.permissions_text, getString(R.string.app_name))

        model.needContactsPermissions.observe(viewLifecycleOwner, Observer { needContacts ->
            if (needContacts && model.haveContactsPermissions.value == false)
                requestPermissions(CONTACT_PERMSSIONS, 0)
        })
        model.needCalendarPermissions.observe(viewLifecycleOwner, Observer { needCalendars ->
            if (needCalendars && model.haveCalendarPermissions.value == false)
                requestPermissions(CALENDAR_PERMISSIONS, 0)
        })
        model.needTasksPermissions.observe(viewLifecycleOwner, Observer { needTasks ->
            if (needTasks == true && model.haveTasksPermissions.value == false)
                requestPermissions(TASKS_PERMISSIONS, 0)
        })
        model.needAllPermissions.observe(viewLifecycleOwner, Observer { needAll ->
            if (needAll && model.haveAllPermissions.value == false) {
                val all = CONTACT_PERMSSIONS + CALENDAR_PERMISSIONS +
                    if (model.haveTasksPermissions.value != null) TASKS_PERMISSIONS else emptyArray()
                requestPermissions(all, 0)
            }
        })

        binding.appSettings.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
            if (intent.resolveActivity(requireActivity().packageManager) != null)
                startActivity(intent)
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        model.checkPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        model.checkPermissions()
    }


    class Model(app: Application): AndroidViewModel(app) {

        val haveContactsPermissions = MutableLiveData<Boolean>()
        val needContactsPermissions = MutableLiveData<Boolean>()
        val haveCalendarPermissions = MutableLiveData<Boolean>()
        val needCalendarPermissions = MutableLiveData<Boolean>()

        val haveTasksPermissions = MutableLiveData<Boolean>()
        val needTasksPermissions = MutableLiveData<Boolean>()
        val tasksWatcher = object: PackageChangedReceiver(app) {
            override fun onReceive(context: Context?, intent: Intent?) {
                checkPermissions()
            }
        }

        val haveAllPermissions = MutableLiveData<Boolean>()
        val needAllPermissions = MutableLiveData<Boolean>()

        init {
            checkPermissions()
        }

        override fun onCleared() {
            tasksWatcher.close()
        }

        fun checkPermissions() {
            val contactPermissions = havePermissions(getApplication(), CONTACT_PERMSSIONS)
            haveContactsPermissions.value = contactPermissions
            needContactsPermissions.value = contactPermissions

            val calendarPermissions = havePermissions(getApplication(), CALENDAR_PERMISSIONS)
            haveCalendarPermissions.value = calendarPermissions
            needCalendarPermissions.value = calendarPermissions

            val tasksAvailable = LocalTaskList.tasksProviderAvailable(getApplication())
            var tasksPermissions: Boolean? = null
            if (tasksAvailable) {
                tasksPermissions = havePermissions(getApplication(), TASKS_PERMISSIONS)
                haveTasksPermissions.value = tasksPermissions
                needTasksPermissions.value = tasksPermissions
            } else {
                haveTasksPermissions.value = null
                needTasksPermissions.value = null
            }

            val allPermissions = contactPermissions && calendarPermissions && (!tasksAvailable || tasksPermissions == true)
            haveAllPermissions.value = allPermissions
            needAllPermissions.value = allPermissions
        }

    }

}