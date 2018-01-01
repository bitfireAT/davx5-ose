/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.TaskProvider
import kotlinx.android.synthetic.main.activity_permissions.*

class PermissionsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val noCalendarPermissions =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
        calendar_permissions.visibility = if (noCalendarPermissions) View.VISIBLE else View.GONE

        val noContactsPermissions =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED
        contacts_permissions.visibility = if (noContactsPermissions) View.VISIBLE else View.GONE

        val noTaskPermissions: Boolean
        if (LocalTaskList.tasksProviderAvailable(this)) {
            noTaskPermissions =
                    ActivityCompat.checkSelfPermission(this, TaskProvider.PERMISSION_READ_TASKS) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, TaskProvider.PERMISSION_WRITE_TASKS) != PackageManager.PERMISSION_GRANTED
            findViewById<View>(R.id.opentasks_permissions).visibility = if (noTaskPermissions) View.VISIBLE else View.GONE
        } else {
            findViewById<View>(R.id.opentasks_permissions).visibility = View.GONE
            noTaskPermissions = false
        }

        if (!noCalendarPermissions && !noContactsPermissions && !noTaskPermissions) {
            val nm = NotificationUtils.createChannels(this)
            nm.cancel(Constants.NOTIFICATION_PERMISSIONS)

            finish()
        }
    }

    fun requestCalendarPermissions(v: View) {
        ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
        ), 0)
    }

    fun requestContactsPermissions(v: View) {
        ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
        ), 0)
    }

    fun requestOpenTasksPermissions(v: View) {
        ActivityCompat.requestPermissions(this, arrayOf(
                TaskProvider.PERMISSION_READ_TASKS,
                TaskProvider.PERMISSION_WRITE_TASKS
        ), 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }
}
