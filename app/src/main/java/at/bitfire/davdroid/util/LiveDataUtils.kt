/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

object LiveDataUtils {

    /**
     * Combines multiple [LiveData] inputs with logical OR to another [LiveData].
     *
     * It's value is *null* as soon as no input is added or as long as no input
     * has emitted a value. As soon as at least one input has emitted a value,
     * the value of the combined object becomes *true* or *false*.
     *
     * @param inputs inputs to be combined with logical OR
     * @return [LiveData] that is *true* when at least one input becomes *true*; *false* otherwise
     */
    fun liveDataLogicOr(inputs: Iterable<LiveData<Boolean>>) = object : MediatorLiveData<Boolean>() {
        init {
            inputs.forEach { liveData ->
                addSource(liveData) {
                    recalculate()
                }
            }
        }

        fun recalculate() {
            value = inputs.any { it.value == true }
        }
    }

}