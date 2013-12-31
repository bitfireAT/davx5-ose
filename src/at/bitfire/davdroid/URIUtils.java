package at.bitfire.davdroid;

import android.annotation.SuppressLint;
import android.util.Log;

@SuppressLint("DefaultLocale")
public class URIUtils {
	private static final String TAG = "davdroid.URIUtils";

	
	// handles invalid URLs/paths as good as possible
	public static String sanitize(String original) {
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
			Log.w(TAG, "Trying to repair invalid URL: " + original + " -> " + url);
		return url;
	}
}
