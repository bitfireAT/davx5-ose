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

    private val currentlyCreating = HashSet<Any>()

    private val singletons = mutableMapOf<Any, WeakReference<Any>>()


    /**
     * Gets a singleton instance of the class, using the Class [T] as key.
     *
     * This method is thread-safe.
     */
    inline fun<reified T> getInstance(noinline createInstance: () -> T): T =
        getInstanceByKey(T::class.java, createInstance)

    /**
     * Gets a singleton instance of the class, using the Class [T] as key.
     *
     * Accepts an Android Context, which is used to determine the [Context.getApplicationContext].
     * The application Context is then passed to [createInstance].
     *
     * This method is thread-safe.
     */
    inline fun<reified T> getInstance(context: Context, noinline createInstance: (appContext: Context) -> T): T =
        getInstanceByKey(T::class.java) {
            createInstance(context.applicationContext)
        }


    @Synchronized
    fun dropAll() {
        singletons.clear()
    }

    /**
     * Gets a singleton instance of the class (using a given key).
     *
     * This method is thread-safe.
     *
     * @param key             unique key (only one instance is created for this key)
     * @param createInstance  creates the instance
     *
     * @throws IllegalStateException  when called recursively with the same key
     */
    @Synchronized
    fun<T> getInstanceByKey(key: Any, createInstance: () -> T): T {
        var cached = singletons[key]
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
        if (currentlyCreating.contains(key))
            throw IllegalStateException("Singleton.getInstance must not be called recursively")
        currentlyCreating += key
        // actually create the instance
        try {
            val newInstance = createInstance()
            singletons[key] = WeakReference(newInstance)
            return newInstance
        } finally {
            currentlyCreating -= key
        }
    }

}