package at.bitfire.davdroid

import org.junit.Assert.*
import org.junit.Test
import java.lang.ref.WeakReference

class SingletonTest {

    @Test
    fun test_Cache() {
        val obj1 = Singleton.getInstance { Object() }
        assertEquals(obj1, Singleton.getInstance<Object> {
            fail("No new Object must be created")
            Object()
        })
    }

    @Test
    fun test_CacheUsesWeakReferences() {
        var obj1: Object? = Singleton.getInstance { Object() }
        val refObj1 = WeakReference(obj1)
        obj1 = null

        // no reference anymore, validate
        System.gc()
        assertNull(refObj1.get())

        // create a new instance
        val obj2 = Singleton.getInstance { Object() }
        assertEquals(obj2, Singleton.getInstance<Object> {
            fail("No new Object must be created")
            Object()
        })
    }

}