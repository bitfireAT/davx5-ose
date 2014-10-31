package at.bitfire.davdroid.webdav;

import java.io.IOException;

import javax.net.ssl.SSLSocket;

import android.util.Log;
import junit.framework.TestCase;

public class TlsSniSocketFactoryTest extends TestCase {
	private static final String TAG = "davdroid.TlsSniSocketFactoryTest";
	
	public void testCiphers() throws IOException {
		SSLSocket socket = (SSLSocket)TlsSniSocketFactory.INSTANCE.createSocket(null);

		Log.i(TAG, "Enabled:");
		for (String cipher : socket.getEnabledCipherSuites())
			Log.i(TAG, cipher);
		
		Log.i(TAG, "Supported:");
		for (String cipher : socket.getSupportedCipherSuites())
			Log.i(TAG, cipher);
		
		assert(true);
	}

}
