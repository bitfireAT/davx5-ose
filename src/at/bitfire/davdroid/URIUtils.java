package at.bitfire.davdroid;

import java.net.URI;
import java.net.URISyntaxException;

import android.annotation.SuppressLint;
import android.util.Log;

@SuppressLint("DefaultLocale")
public class URIUtils {
	private static final String TAG = "davdroid.URIUtils";
	
	public static boolean isSame(URI a, URI b) {
	     try {
	    	a = new URI(a.getScheme(), null, a.getHost(), a.getPort(), sanitize(a.getPath()), sanitize(a.getQuery()), null);
	    	b = new URI(b.getScheme(), null, b.getHost(), b.getPort(), sanitize(b.getPath()), sanitize(b.getQuery()), null);
			return a.equals(b);
		} catch (URISyntaxException e) {
			return false;
		}
	}
	
	// handles invalid URLs/paths as good as possible
	public static String sanitize(String original) {
		if (original == null)
			return null;
		
		String url = original;

		// ":" is reserved as scheme/port separator, but we assume http:// and https:// URLs only
		// and will encode ":" in URLs without one of these schemata
		if (!url.toLowerCase().startsWith("http://") &&		// ":" is valid scheme separator
			!url.toLowerCase().startsWith("https://") &&	// ":" is valid scheme separator
			!url.startsWith("//"))							// ":" may be valid port separator
			url = url.replace(":", "%3A");					// none of these -> ":" is not used for reserved purpose -> must be encoded 
		
		// rewrite reserved characters:
		// 	"@" should be used for user name/password, but this case shouldn't appear in our URLs
		// rewrite unsafe characters, too:
		//	" ", "<", ">", """, "#", "{", "}", "|", "\", "^", "~", "["], "]", "`"
		// do not rewrite "%" because we assume that URLs should be already encoded correctly
		for (char c : new char[] { '@', ' ', '<', '>', '"', '#', '{', '}', '|', '\\', '^', '~', '[', ']', '`' })
			url = url.replace(String.valueOf(c), "%" + Integer.toHexString(c));
		
		if (!url.equals(original))
			Log.w(TAG, "Tried to repair invalid URL/URL path: " + original + " -> " + url);
		return url;
	}
}
