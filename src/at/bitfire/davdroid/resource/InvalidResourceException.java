package at.bitfire.davdroid.resource;

public class InvalidResourceException extends Exception {
	private static final long serialVersionUID = 1593585432655578220L;
	
	public InvalidResourceException(String message) {
		super(message);
	}

	public InvalidResourceException(Throwable throwable) {
		super(throwable);
	}
}
