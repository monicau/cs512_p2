package TransactionManager;

/*
    The transaction is deadlocked.  Somebody should abort it.
*/

public class InvalidTransactionException extends Exception
{
	public InvalidTransactionException(String message) {
		super(message);
	}
	public InvalidTransactionException() {
		super();
	}
}
