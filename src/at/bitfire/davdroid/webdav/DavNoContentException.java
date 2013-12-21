package at.bitfire.davdroid.webdav;

public class DavNoContentException extends DavException {
	private static final long serialVersionUID = 6256645020350945477L;
	
	private final static String message = "HTTP response entity (content) expected but not received";

	public DavNoContentException() {
		super(message);
	}
}
