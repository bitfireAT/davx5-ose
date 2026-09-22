/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

class LocalNetworkPermissionRequiredException(val hostname: String) : SecurityException("The app requires the Local Network permission to access this hostname ($hostname)")
