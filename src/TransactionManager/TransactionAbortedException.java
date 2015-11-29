package TransactionManager;

public class TransactionAbortedException extends Exception {
	private static final long serialVersionUID = -6223148281195298117L;
	
	public TransactionAbortedException(String message) {
		super(message);
	}
	
}
