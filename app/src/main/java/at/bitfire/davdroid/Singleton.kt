package at.bitfire.davdroid

import android.content.Context
import java.lang.ref.WeakReference

/**
 * A singleton registry that guarantees that there is not more than one instance per class.
 *
 * It uses weak references so that as soon as the singletons are not used anymore, they can be
 * freed by GC.
 */
object Singleton {

    private val currentlyCreating = HashSet<Class<*>>()

    private val singletons = mutableMapOf<Any, WeakReference<Any>>()


    inline fun<reified T> getInstance(noinline createInstance: () -> T): T =
        getInstance(T::class.java, createInstance)

    inline fun<reified T> getInstance(context: Context, noinline createInstance: (appContext: Context) -> T): T =
        getInstance(T::class.java) {
            createInstance(context.applicationContext)
        }


    @Synchronized
    fun dropAll() {
        singletons.clear()
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

        // CREATE NEW SINGLETON
        // prevent recursive creation
        if (currentlyCreating.contains(clazz))
            throw IllegalStateException("Singleton.getInstance must not be called recursively")
        currentlyCreating += clazz
        // actually create the instance
        try {
            val newInstance = createInstance()
            singletons[clazz] = WeakReference(newInstance)
            return newInstance
        } finally {
            currentlyCreating -= clazz
        }
    }

}