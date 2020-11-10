package at.bitfire.davdroid.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.PackageChangedReceiver
import at.bitfire.davdroid.PermissionUtils
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

        model.needAutoResetPermission.observe(viewLifecycleOwner, { keepPermissions ->
            if (keepPermissions == true && model.haveAutoResetPermission.value == false) {
                Toast.makeText(requireActivity(), R.string.permissions_autoreset_instruction, Toast.LENGTH_LONG).show()
                startActivity(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)))
            }
        })
        model.needContactsPermissions.observe(viewLifecycleOwner, { needContacts ->
            if (needContacts && model.haveContactsPermissions.value == false)
                requestPermissions(CONTACT_PERMISSIONS, 0)
        })
        model.needCalendarPermissions.observe(viewLifecycleOwner, { needCalendars ->
            if (needCalendars && model.haveCalendarPermissions.value == false)
                requestPermissions(CALENDAR_PERMISSIONS, 0)
        })
        model.needOpenTasksPermissions.observe(viewLifecycleOwner, { needOpenTasks ->
            if (needOpenTasks == true && model.haveOpenTasksPermissions.value == false)
                requestPermissions(TaskProvider.PERMISSIONS_OPENTASKS, 0)
        })
        model.needTasksOrgPermissions.observe(viewLifecycleOwner, { needTasksOrg ->
            if (needTasksOrg == true && model.haveTasksOrgPermissions.value == false)
                requestPermissions(TaskProvider.PERMISSIONS_TASKS_ORG, 0)
        })
        model.needAllPermissions.observe(viewLifecycleOwner, { needAll ->
            if (needAll && model.haveAllPermissions.value == false) {
                val all = mutableSetOf(*CONTACT_PERMISSIONS, *CALENDAR_PERMISSIONS)
                if (model.haveOpenTasksPermissions.value != null)
                    all.addAll(TaskProvider.PERMISSIONS_OPENTASKS)
                if (model.haveTasksOrgPermissions.value != null)
                    all.addAll(TaskProvider.PERMISSIONS_TASKS_ORG)
                requestPermissions(all.toTypedArray(), 0)
            }
        })

        binding.appSettings.setOnClickListener {
            PermissionUtils.showAppSettings(requireActivity())
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

        val haveAutoResetPermission = MutableLiveData<Boolean>()
        val needAutoResetPermission = MutableLiveData<Boolean>()

        val haveContactsPermissions = MutableLiveData<Boolean>()
        val needContactsPermissions = MutableLiveData<Boolean>()
        val haveCalendarPermissions = MutableLiveData<Boolean>()
        val needCalendarPermissions = MutableLiveData<Boolean>()

        val haveOpenTasksPermissions = MutableLiveData<Boolean>()
        val needOpenTasksPermissions = MutableLiveData<Boolean>()
        val haveTasksOrgPermissions = MutableLiveData<Boolean>()
        val needTasksOrgPermissions = MutableLiveData<Boolean>()
        val tasksWatcher = object: PackageChangedReceiver(app) {
            @MainThread
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

        @MainThread
        fun checkPermissions() {
            val pm = getApplication<Application>().packageManager

            // auto-reset permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val keepPermissions = pm.isAutoRevokeWhitelisted
                haveAutoResetPermission.value = keepPermissions
                needAutoResetPermission.value = keepPermissions
            }

            // other permissions
            val contactPermissions = havePermissions(getApplication(), CONTACT_PERMISSIONS)
            haveContactsPermissions.value = contactPermissions
            needContactsPermissions.value = contactPermissions

            val calendarPermissions = havePermissions(getApplication(), CALENDAR_PERMISSIONS)
            haveCalendarPermissions.value = calendarPermissions
            needCalendarPermissions.value = calendarPermissions

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
                haveTasksOrgPermissions.value = null
                needTasksOrgPermissions.value = null
            }

            // "all permissions" switch
            val allPermissions = contactPermissions &&
                    calendarPermissions &&
                    (!openTasksAvailable || openTasksPermissions == true) &&
                    (!tasksOrgAvailable || tasksOrgPermissions == true)
            haveAllPermissions.value = allPermissions
            needAllPermissions.value = allPermissions
        }

    }

}
