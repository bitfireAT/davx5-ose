package at.bitfire.davdroid.resource;

public class RecordNotFoundException extends LocalStorageException {
	private static final long serialVersionUID = 4961024282198632578L;
	
	private static final String detailMessage = "Record not found in local content provider"; 
	
	
	RecordNotFoundException(Throwable ex) {
		super(detailMessage, ex);
	}
	
	RecordNotFoundException() {
		super(detailMessage);
	}

}
