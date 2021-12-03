package at.bitfire.davdroid

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference

class SingletonTest {

    @Before
    fun prepare() {
        Singleton.dropAll()
    }


    @Test
    fun testCache() {
        val obj1 = Singleton.getInstance { Any() }
        assertEquals(obj1, Singleton.getInstance {
            fail("No new Any must be created")
            Any()
        })
    }

    @Test
    fun testCacheUsesWeakReferences() {
        var obj1: Any? = Singleton.getInstance { Any() }
        val refObj1 = WeakReference(obj1)
        obj1 = null

        // no reference anymore, validate
        System.gc()
        Runtime.getRuntime().gc()
        assertNull(refObj1.get())

        // create a new instance
        val obj2 = Singleton.getInstance { Any() }
        assertEquals(obj2, Singleton.getInstance {
            fail("No new Any must be created")
            Any()
        })
    }

    @Test(expected = IllegalStateException::class)
    fun testRecursive() {
        Singleton.getInstance() {
            Singleton.getInstance() {
                Any()
            }
        }
    }

}