package at.bitfire.davdroid;

import java.net.URI;
import java.net.URISyntaxException;

public class Utils {
	public static boolean isSameURL(URI a, URI b) {
	     try {
	    	a = new URI(a.getScheme(), null, a.getHost(), a.getPort(), a.getPath(), a.getQuery(), a.getFragment());
	    	b = new URI(b.getScheme(), null, b.getHost(), b.getPort(), b.getPath(), b.getQuery(), b.getFragment());
			return a.equals(b);
		} catch (URISyntaxException e) {
			return false;
		}
	}

	public static URI resolveURI(URI parent, String member) {
		if (!member.startsWith("/") && !member.startsWith("http:") && !member.startsWith("https://"))
			member = "./" + member;
		
		return parent.resolve(member);
	}
}
