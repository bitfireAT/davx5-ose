package at.bitfire.davdroid

import android.content.Context

abstract class AndroidSingleton<T> {

    var creatingSingleton = false
    var singleton: T? = null

    @Synchronized
    fun getInstance(context: Context): T {
        singleton?.let {
            return it
        }

        if (creatingSingleton)
            throw IllegalStateException("AndroidSingleton::getInstance() must not be called while createInstance()")
        creatingSingleton = true

        val newSingleton = createInstance(context.applicationContext)
        singleton = newSingleton

        creatingSingleton = false
        return newSingleton
    }

    abstract fun createInstance(context: Context): T

}