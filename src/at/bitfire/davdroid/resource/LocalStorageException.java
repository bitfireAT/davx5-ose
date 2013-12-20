package at.bitfire.davdroid.resource;

public class LocalStorageException extends Exception {
	private static final long serialVersionUID = -7787658815291629529L;
	
	private static final String detailMessage = "Couldn't access local content provider";
	

	public LocalStorageException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public LocalStorageException(String detailMessage) {
		super(detailMessage);
	}
	
	public LocalStorageException(Throwable throwable) {
		super(detailMessage, throwable);
	}

	public LocalStorageException() {
		super(detailMessage);
	}
}
