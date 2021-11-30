package at.bitfire.davdroid

import android.content.Context
import java.lang.ref.WeakReference

object Singleton {

    private val singletons = mutableMapOf<Any, WeakReference<Any>>()


    inline fun<reified T> getInstance(noinline createInstance: () -> T): T =
        getInstance(T::class.java, createInstance)

    inline fun<reified T> getInstance(context: Context, noinline createInstance: (appContext: Context) -> T): T =
        getInstance(T::class.java) {
            createInstance(context.applicationContext)
        }


    @Synchronized
    fun<T> getInstance(clazz: Class<T>, createInstance: () -> T): T {
        var cached = singletons[clazz]
        if (cached != null && cached.get() == null) {
            singletons.remove(cached)
            cached = null
        }

        // found existing singleton
        if (cached != null)
            @Suppress("UNCHECKED_CAST")
            return cached.get() as T

        // create new singleton
        val newInstance = createInstance()
        singletons[clazz] = WeakReference(newInstance)
        return newInstance
    }

}