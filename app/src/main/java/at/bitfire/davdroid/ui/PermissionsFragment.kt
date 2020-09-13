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
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.PackageChangedReceiver
import at.bitfire.davdroid.PermissionUtils.CALENDAR_PERMISSIONS
import at.bitfire.davdroid.PermissionUtils.CONTACT_PERMISSIONS
import at.bitfire.davdroid.PermissionUtils.havePermissions
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityPermissionsBinding
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName

class PermissionsFragment: Fragment() {

    val model by viewModels<Model>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ActivityPermissionsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.model = model

        binding.text.text = getString(R.string.permissions_text, getString(R.string.app_name))

        model.needContactsPermissions.observe(viewLifecycleOwner, Observer { needContacts ->
            if (needContacts && model.haveContactsPermissions.value == false)
                requestPermissions(CONTACT_PERMISSIONS, 0)
        })
        model.needCalendarPermissions.observe(viewLifecycleOwner, Observer { needCalendars ->
            if (needCalendars && model.haveCalendarPermissions.value == false)
                requestPermissions(CALENDAR_PERMISSIONS, 0)
        })
        model.needOpenTasksPermissions.observe(viewLifecycleOwner, Observer { needOpenTasks ->
            if (needOpenTasks == true && model.haveOpenTasksPermissions.value == false)
                requestPermissions(TaskProvider.PERMISSIONS_OPENTASKS, 0)
        })
        model.needTasksOrgPermissions.observe(viewLifecycleOwner, Observer { needTasksOrg ->
            if (needTasksOrg == true && model.haveTasksOrgPermissions.value == false)
                requestPermissions(TaskProvider.PERMISSIONS_TASKS_ORG, 0)
        })
        model.needAllPermissions.observe(viewLifecycleOwner, Observer { needAll ->
            if (needAll && model.haveAllPermissions.value == false) {
                val all = CONTACT_PERMISSIONS + CALENDAR_PERMISSIONS +
                    if (model.haveOpenTasksPermissions.value != null) TaskProvider.PERMISSIONS_OPENTASKS else emptyArray<String>() +
                    if (model.haveTasksOrgPermissions.value != null) TaskProvider.PERMISSIONS_TASKS_ORG else emptyArray<String>()
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

        val haveOpenTasksPermissions = MutableLiveData<Boolean>()
        val needOpenTasksPermissions = MutableLiveData<Boolean>()
        val haveTasksOrgPermissions = MutableLiveData<Boolean>()
        val needTasksOrgPermissions = MutableLiveData<Boolean>()
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
            val contactPermissions = havePermissions(getApplication(), CONTACT_PERMISSIONS)
            haveContactsPermissions.value = contactPermissions
            needContactsPermissions.value = contactPermissions

            val calendarPermissions = havePermissions(getApplication(), CALENDAR_PERMISSIONS)
            haveCalendarPermissions.value = calendarPermissions
            needCalendarPermissions.value = calendarPermissions

            val pm = getApplication<Application>().packageManager
            // OpenTasks
            val openTasksAvailable = pm.resolveContentProvider(ProviderName.OpenTasks.authority, 0) != null
            var openTasksPermissions: Boolean? = null
            if (openTasksAvailable) {
                openTasksPermissions = havePermissions(getApplication(), TaskProvider.PERMISSIONS_OPENTASKS)
                haveOpenTasksPermissions.value = openTasksPermissions
                needOpenTasksPermissions.value = openTasksPermissions
            } else {
                haveOpenTasksPermissions.value = null
                needOpenTasksPermissions.value = null
            }
            // tasks.org
            val tasksOrgAvailable = pm.resolveContentProvider(ProviderName.TasksOrg.authority, 0) != null
            var tasksOrgPermissions: Boolean? = null
            if (tasksOrgAvailable) {
                tasksOrgPermissions = havePermissions(getApplication(), TaskProvider.PERMISSIONS_TASKS_ORG)
                haveTasksOrgPermissions.value = tasksOrgPermissions
                needTasksOrgPermissions.value = tasksOrgPermissions
            } else {
                haveOpenTasksPermissions.value = null
                needOpenTasksPermissions.value = null
            }

            val allPermissions = contactPermissions &&
                    calendarPermissions &&
                    (!openTasksAvailable || openTasksPermissions == true) &&
                    (!tasksOrgAvailable || tasksOrgPermissions == true)
            haveAllPermissions.value = allPermissions
            needAllPermissions.value = allPermissions
        }

    }

}