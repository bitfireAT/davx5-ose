package at.bitfire.davdroid.webdav;

import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.webdav.GzipDecompressingEntity;
import at.bitfire.davdroid.webdav.TlsSniSocketFactory;

// see AndroidHttpClient


public class DavHttpClient extends DefaultHttpClient {
	private static DavHttpClient httpClient = null;
	
	private DavHttpClient(ClientConnectionManager connectionManager, HttpParams params) {
		super(connectionManager, params);
	}
	
	
	public static synchronized DefaultHttpClient getInstance() {
		if (httpClient == null) {
			HttpParams params = new BasicHttpParams();
			params.setParameter(CoreProtocolPNames.USER_AGENT, "DAVdroid/" + Constants.APP_VERSION);
			
			// use defaults of AndroidHttpClient
			HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
			HttpConnectionParams.setSoTimeout(params, 20 * 1000);
			HttpConnectionParams.setSocketBufferSize(params, 8192);
			HttpConnectionParams.setStaleCheckingEnabled(params, false);
			
			// don't allow redirections
			HttpClientParams.setRedirecting(params, false);
			
			// use our own, SNI-capable LayeredSocketFactory for https://
		    SchemeRegistry schemeRegistry = new SchemeRegistry();
		    schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		    schemeRegistry.register(new Scheme("https", new TlsSniSocketFactory(), 443));
		    
			httpClient = new DavHttpClient(new ThreadSafeClientConnManager(params, schemeRegistry), params);
			
			// allow gzip compression
			GzipDecompressingEntity.enable(httpClient);
		}
		
		return httpClient;
	}
	
}
