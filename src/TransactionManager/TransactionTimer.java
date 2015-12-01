package TransactionManager;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class TransactionTimer {

	public class Txn{
		State state;
		long time;
		public Txn() {
			state = State.Active;
			time = System.currentTimeMillis();
		}
	}
	
	public enum State{
		Active, Aborted, Committed;
	}
	
	Map<Integer, Txn> transactions = new ConcurrentHashMap<>();
	private long ttl;
	private Function<Integer, Boolean> abort;
	private Thread thread;
	public TransactionTimer(long ttl, Function<Integer,Boolean> abort) {
		this.ttl = ttl ;
		this.abort = abort;
	}
	
	// Start the timer
	public void start(){
		thread = new Thread(() ->{
			while(!Thread.interrupted()){
				Iterator<Entry<Integer, Txn>> it = transactions.entrySet().iterator();
				while(it.hasNext()){
					
					Entry<Integer, Txn> transaction = it.next();
					if(transaction.getValue().state == State.Active){
						if(System.currentTimeMillis() - transaction.getValue().time > ttl ){
							System.out.println("Sweeper detects old txn! aborting " + transaction.getKey());
							transaction.getValue().state = State.Aborted;
							Boolean success = abort.apply(transaction.getKey());
							if(!success) System.out.println("Failed to abort; Aborting to abort");
						}
					}
				}
				try {
					Thread.sleep(500);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		thread.start();
	}
	
	public void ping(int id){
		// Add transaction to map if it doesn't exist
		transactions.computeIfAbsent(id, i -> new Txn());
		// Reset its time
		transactions.get(id).time = System.currentTimeMillis();
	}
	
	public void kill(){
		thread.interrupt();
	}

	public boolean isActive(int id) {
		// Tells us if transaction is active or not
		Txn t = transactions.get(id);
		if (t!=null) return t.state == State.Active;
		return false;
	}
	
	public boolean isAborted(int id) {
		// Tells us if transaction is aborted or not
		return transactions.get(id).state == State.Aborted;
	}
	
	public void setState(int id, State type) {
		// Set transaction to be committed so sweeper stops tracking it
		transactions.get(id).state = type;
	}
}
