/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
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


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = ActivityPermissionsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.model = model

        binding.text.text = getString(R.string.permissions_text, getString(R.string.app_name))

        val requestPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            model.checkPermissions()
        }

        model.needAutoResetPermission.observe(viewLifecycleOwner, { keepPermissions ->
            if (keepPermissions == true && model.haveAutoResetPermission.value == false) {
                Toast.makeText(requireActivity(), R.string.permissions_autoreset_instruction, Toast.LENGTH_LONG).show()
                startActivity(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)))
            }
        })
        model.needContactsPermissions.observe(viewLifecycleOwner, { needContacts ->
            if (needContacts && model.haveContactsPermissions.value == false)
                requestPermission.launch(CONTACT_PERMISSIONS)
        })
        model.needCalendarPermissions.observe(viewLifecycleOwner, { needCalendars ->
            if (needCalendars && model.haveCalendarPermissions.value == false)
                requestPermission.launch(CALENDAR_PERMISSIONS)
        })
        model.needNotificationPermissions.observe(viewLifecycleOwner, { needNotifications ->
            if (needNotifications == true && model.haveNotificationPermissions.value == false)
                requestPermission.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        })
        model.needOpenTasksPermissions.observe(viewLifecycleOwner, { needOpenTasks ->
            if (needOpenTasks == true && model.haveOpenTasksPermissions.value == false)
                requestPermission.launch(TaskProvider.PERMISSIONS_OPENTASKS)
        })
        model.needTasksOrgPermissions.observe(viewLifecycleOwner, { needTasksOrg ->
            if (needTasksOrg == true && model.haveTasksOrgPermissions.value == false)
                requestPermission.launch(TaskProvider.PERMISSIONS_TASKS_ORG)
        })
        model.needJtxPermissions.observe(viewLifecycleOwner, { needJtx ->
            if (needJtx == true && model.haveJtxPermissions.value == false)
                requestPermission.launch(TaskProvider.PERMISSIONS_JTX)
        })
        model.needAllPermissions.observe(viewLifecycleOwner, { needAll ->
            if (needAll && model.haveAllPermissions.value == false) {
                val all = mutableSetOf(*CONTACT_PERMISSIONS, *CALENDAR_PERMISSIONS, Manifest.permission.POST_NOTIFICATIONS)
                if (model.haveOpenTasksPermissions.value != null)
                    all.addAll(TaskProvider.PERMISSIONS_OPENTASKS)
                if (model.haveTasksOrgPermissions.value != null)
                    all.addAll(TaskProvider.PERMISSIONS_TASKS_ORG)
                if (model.haveJtxPermissions.value != null)
                    all.addAll(TaskProvider.PERMISSIONS_JTX)
                requestPermission.launch(all.toTypedArray())
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


    class Model(app: Application): AndroidViewModel(app) {

        val haveAutoResetPermission = MutableLiveData<Boolean>()
        val needAutoResetPermission = MutableLiveData<Boolean>()

        val haveContactsPermissions = MutableLiveData<Boolean>()
        val needContactsPermissions = MutableLiveData<Boolean>()
        val haveCalendarPermissions = MutableLiveData<Boolean>()
        val needCalendarPermissions = MutableLiveData<Boolean>()
        val haveNotificationPermissions = MutableLiveData<Boolean>()
        val needNotificationPermissions = MutableLiveData<Boolean>()

        val haveOpenTasksPermissions = MutableLiveData<Boolean>()
        val needOpenTasksPermissions = MutableLiveData<Boolean>()
        val haveTasksOrgPermissions = MutableLiveData<Boolean>()
        val needTasksOrgPermissions = MutableLiveData<Boolean>()
        val haveJtxPermissions = MutableLiveData<Boolean>()
        val needJtxPermissions = MutableLiveData<Boolean>()
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

            val notificationPermissions: Boolean
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13: POST_NOTIFICATIONS permission required
                notificationPermissions =
                    ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                haveNotificationPermissions.value = notificationPermissions
                needNotificationPermissions.value = notificationPermissions
            } else {
                // Android <= 12: hide the POST_NOTIFICATIONS switch
                notificationPermissions = true
                haveNotificationPermissions.value = null
                needNotificationPermissions.value = null
            }

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
            // jtx Board
            val jtxAvailable = pm.resolveContentProvider(ProviderName.JtxBoard.authority, 0) != null
            var jtxPermissions: Boolean? = null
            if (jtxAvailable) {
                jtxPermissions = havePermissions(getApplication(), TaskProvider.PERMISSIONS_JTX)
                haveJtxPermissions.value = jtxPermissions
                needJtxPermissions.value = jtxPermissions
            } else {
                haveJtxPermissions.value = null
                needJtxPermissions.value = null
            }

            // "all permissions" switch
            val allPermissions = contactPermissions &&
                    calendarPermissions &&
                    notificationPermissions &&
                    (!openTasksAvailable || openTasksPermissions == true) &&
                    (!tasksOrgAvailable || tasksOrgPermissions == true) &&
                    (!jtxAvailable || jtxPermissions == true)
            haveAllPermissions.value = allPermissions
            needAllPermissions.value = allPermissions
        }

    }

}
