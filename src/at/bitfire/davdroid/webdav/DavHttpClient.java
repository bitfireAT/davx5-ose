package at.bitfire.davdroid.webdav;

import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import at.bitfire.davdroid.Constants;

// see AndroidHttpClient


public class DavHttpClient extends DefaultHttpClient {
	private DavHttpClient(HttpParams params) {
		super(params);
	}
	
	
	public static DefaultHttpClient getDefault() {
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.USER_AGENT, "DAVdroid/" + Constants.APP_VERSION);
		
		// use defaults of AndroidHttpClient
		HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
		HttpConnectionParams.setSoTimeout(params, 20 * 1000);
		HttpConnectionParams.setSocketBufferSize(params, 8192);
		HttpConnectionParams.setStaleCheckingEnabled(params, false);
		
		// don't allow redirections
		HttpClientParams.setRedirecting(params, false);
		
		DavHttpClient httpClient = new DavHttpClient(params);
		
		// use our own, SNI-capable LayeredSocketFactory for https://
	    SchemeRegistry schemeRegistry = httpClient.getConnectionManager().getSchemeRegistry();
	    schemeRegistry.register(new Scheme("https", new TlsSniSocketFactory(), 443));
		
		// allow gzip compression
		GzipDecompressingEntity.enable(httpClient);
		return httpClient;
	}
	
}
