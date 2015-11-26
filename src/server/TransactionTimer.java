package server;

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
	
	public void start(){
		thread = new Thread(() ->{
			while(!Thread.interrupted()){
				Iterator<Entry<Integer, Txn>> it = transactions.entrySet().iterator();
				while(it.hasNext()){
					
					Entry<Integer, Txn> transaction = it.next();
					
					if(transaction.getValue().state == State.Active){
						if(transaction.getValue().time - System.currentTimeMillis() > ttl ){
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
		transactions.computeIfAbsent(id, i -> new Txn());
		transactions.get(id).time = System.currentTimeMillis();
	}
	
	public void kill(){
		thread.interrupt();
	}
}
