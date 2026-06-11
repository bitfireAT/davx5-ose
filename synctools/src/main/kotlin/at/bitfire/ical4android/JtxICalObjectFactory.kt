/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.ical4android

import android.content.ContentValues

interface JtxICalObjectFactory<out T: JtxICalObject> {

    fun fromProvider(collection: JtxCollection, values: ContentValues): T

}