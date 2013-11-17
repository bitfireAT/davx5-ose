package at.bitfire.davdroid.webdav;

import org.apache.http.HttpException;

public class InvalidDavResponseException extends HttpException {
	private static final long serialVersionUID = -2118919144443165706L;
	
	public InvalidDavResponseException(String message) {
		super("Invalid DAV response: " + message);
	}
}
