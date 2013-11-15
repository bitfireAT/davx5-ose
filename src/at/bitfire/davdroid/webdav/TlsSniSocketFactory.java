package at.bitfire.davdroid.webdav;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.params.HttpParams;

import android.annotation.TargetApi;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class TlsSniSocketFactory implements LayeredSocketFactory {
	private static final String TAG = "davdroid.SNISocketFactory";
	
	// "insecure" means that it doesn't verify the host name
	// we will do this ourselves so we can set up SNI before
	SSLCertificateSocketFactory sslSocketFactory =
			(SSLCertificateSocketFactory) SSLCertificateSocketFactory.getInsecure(0, null);

	
	// Plain TCP/IP (layer below TLS)

	@Override
	public Socket connectSocket(Socket s, String host, int port, InetAddress localAddress, int localPort, HttpParams params) throws IOException {
		s.connect(new InetSocketAddress(host, port));
		return s;
	}

	@Override
	public Socket createSocket() {
		Socket s = new Socket();
		return s;
	}

	@Override
	public boolean isSecure(Socket s) throws IllegalArgumentException {
		if (s instanceof SSLSocket)
			return ((SSLSocket)s).isConnected();
		return false;
	}

	
	// TLS layer

	@Override
	public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
		SSLSocket ssl = (SSLSocket)sslSocketFactory.createSocket(s, host, port, autoClose);

		// set SNI before the handshake
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			Log.i(TAG, "Setting SNI hostname");
			sslSocketFactory.setHostname(ssl, host);
		} else
			Log.w(TAG, "No SNI support below Android 4.2!");
		
		// now do the TLS handshake
		ssl.startHandshake();
		SSLSession session = ssl.getSession();
		if (session == null)
            throw new SSLException("Cannot verify SSL socket without session");
		
		// verify host name (important!)
		if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session))
            throw new SSLPeerUnverifiedException("Cannot verify hostname: " + host);
		return ssl;
	}

}
