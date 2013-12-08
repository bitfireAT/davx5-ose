package at.bitfire.davdroid.test;

import junit.framework.TestCase;
import lombok.NonNull;

/* Lombok has some strange issues under Android:
 * @NonNull always crashes on Android – http://code.google.com/p/projectlombok/issues/detail?id=612
 */

public class LombokTest extends TestCase {
	private String appendSlash(@NonNull String s) {
		return s + "/";
	}

	public void testNonNull() {
		assertNull(appendSlash(null));
		//assertEquals("1/", appendSlash("1"));
	}
}
