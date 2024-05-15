package at.bitfire.davdroid.ui.account

import android.app.Application
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class WifiPermissionsModel @Inject constructor(
    context: Application
): ViewModel() {

    private val locationManager = context.getSystemService<LocationManager>()!!

    val locationEnabled = broadcastReceiverFlow(context, IntentFilter(LocationManager.MODE_CHANGED_ACTION), immediate = true)
        .map { LocationManagerCompat.isLocationEnabled(locationManager) }

}