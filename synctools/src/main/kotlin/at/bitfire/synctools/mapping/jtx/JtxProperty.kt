/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

object JtxProperty {
    // used to define an extended status (additionally to standard status)
    const val X_XSTATUS = "X-STATUS"

    const val X_COMPLETEDTIMEZONE = "X-COMPLETEDTIMEZONE"

    // used to define a Geofence-Radius to notify the user when close
    const val X_GEOFENCE_RADIUS = "X-GEOFENCE-RADIUS"
}
