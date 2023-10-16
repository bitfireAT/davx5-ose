package at.bitfire.davdroid.util

import android.content.Context
import androidx.lifecycle.AndroidViewModel

/**
 * An alias for [AndroidViewModel]s that need to access the application context.
 *
 * Makes it shorter than writing
 * ```kotlin
 * getApplication<Application>()
 * ```
 */
val AndroidViewModel.context: Context
    get() = getApplication()
