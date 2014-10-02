package at.bitfire.davdroid.webdav;

import org.apache.http.HttpStatus;

public class NotAuthorizedException extends HttpException {
	private static final long serialVersionUID = 2490525047224413586L;

	public NotAuthorizedException(String reason) {
		super(HttpStatus.SC_UNAUTHORIZED, reason);
	}
	
}
