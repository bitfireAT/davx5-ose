package at.bitfire.davdroid.webdav;

public class DavNoMultiStatusException extends DavException {
	private static final long serialVersionUID = -3600405724694229828L;
	
	private final static String message = "207 Multi-Status expected but not received";
	
	public DavNoMultiStatusException() {
		super(message);
	}
}
