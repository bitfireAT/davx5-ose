package at.bitfire.davdroid.webdav;

import org.apache.http.HttpException;

public class DavException extends HttpException {
	private static final long serialVersionUID = -2118919144443165706L;
	
	final private static String prefix = "Invalid DAV response: ";
	
	
	public DavException(String message) {
		super(prefix + message);
	}
	
	public DavException(String message, Throwable ex) {
		super(prefix + message, ex);
	}
}
