package server;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;


public class TransactionManager {
	public enum RM { FLIGHT, CAR, ROOM, CUSTOMER };
	private RMMap<Integer, Vector<RM>> activeRMs;

	//	private int txnCounter;

	private AtomicInteger txnCounter;

	private MiddlewareImpl mw;
	private ResourceManager proxyFlight;
	private ResourceManager proxyCar;
	private ResourceManager proxyRoom;
	private int ttl; // time to live timeout
	private RMMap<Integer, Integer> timeAlive;// key=txnID, value=time alive

	private Thread sweeper;

	public TransactionManager(MiddlewareImpl middleware, ResourceManager flight, ResourceManager car, ResourceManager room, int timeToLive) {
		activeRMs = new RMMap<Integer, Vector<RM>>();
		timeAlive = new RMMap<Integer, Integer>();
		txnCounter = new AtomicInteger();
		mw = middleware;
		proxyFlight = flight;
		proxyCar = car;
		proxyRoom = room;
		ttl = timeToLive;

		sweeper = new Thread(() ->{
			while(!Thread.interrupted()){
				Iterator<Entry<Integer, Integer>> it = timeAlive.entrySet().iterator();
				while(it.hasNext()){
					Entry<Integer, Integer> livedFor = it.next();
					int txnID = livedFor.getKey();
					int timealive = livedFor.getValue();
					Vector<RM> vector = activeRMs.get(txnID);
					timeAlive.replace(txnID, (timealive-500));
					System.out.println("Sweeper checking txn " + txnID + ", timealive: " + timealive + " vs ttl: " + ttl);
					if(timealive <= 0){
						boolean success = true;
						if (vector != null) {
							for (RM rm : vector) {
								switch (rm) {
								case CUSTOMER:
									success &= mw.abortCustomer(txnID) && mw.unlock(txnID);
									break;
								case FLIGHT:
									// abort transaction and unlock in one message
									success &= proxyFlight.abort(txnID);
									break;
								case CAR:
									success &= proxyCar.abort(txnID);
									break;
								case ROOM:
									success &= proxyRoom.abort(txnID);
									break;
								default:
									break;
								}
							}
						}
						System.out.println("TM:: Unlock all locks held by this transaction: " + success); 
						delist(txnID);
					}
				}
				try {
					Thread.sleep(500);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		sweeper.start();
	}

	// Return a new txn ID
	synchronized public int start() {
		int counter = txnCounter.incrementAndGet();

		// Start timing this transaction
		timeAlive.put(counter, ttl);

		return counter;
	}
	public boolean commit(int txnID) {                                                   
		System.out.println("TM:: Committing transaction "+txnID);    
		boolean success = true;
		// Remove txn history on other resource managers and unlock locks
		Vector<RM> rms = activeRMs.get(txnID);
		if (rms != null) {
			for (RM rm : rms) {
				switch (rm) {
				case CUSTOMER:
					System.out.println("TM:: clearing txn history and unlocking customer");
					mw.removeTxn(txnID);
					success &= mw.unlock(txnID);
					break;
				case FLIGHT:
					System.out.println("TM:: clearing txn history and unlocking flight");
					// remove transaction and unlock in one message
					success &= proxyFlight.commit(txnID);
					break;
				case CAR:
					success &= proxyCar.commit(txnID);
					break;
				case ROOM:
					success &= proxyRoom.commit(txnID);
					break;
				default:
					break;
				}
			}
		} else {
			System.out.println("TM:: no RMs involved in this txn.  Committing nothing..");
		}
		System.out.println("TM:: Unlock all locks held by this transaction: " + success);     
		//Remove rm's from activeRM 
		delist(txnID);
		return success;
	}   

	public boolean abort(int txnID) {
		System.out.println("TM:: Aborting a transaction " + txnID);      
		System.out.println("TM:: sending abort to resource managers..");
		boolean success = true;
		// Remove txn history on other resource managers and unlock locks
		Vector<RM> rms = activeRMs.get(txnID);
		if (rms != null) {
			for (RM rm : rms) {
				switch (rm) {
				case CUSTOMER:
					success &= mw.abortCustomer(txnID) && mw.unlock(txnID) ;
					break;
				case FLIGHT:
					// abort transaction and unlock in one message
					success &= proxyFlight.abort(txnID);
					break;
				case CAR:
					success &= proxyCar.abort(txnID);
					break;
				case ROOM:
					success &= proxyRoom.abort(txnID);
					break;
				default:
					break;
				}
			}
		}
		System.out.println("TM:: Unlock all locks held by this transaction: " + success);     
		//Remove rm's from activeRM 
		delist(txnID);
		return success;  
	}

	// Add RM to a txn
	public void enlist(int txnID, RM rm) {
		System.out.println("TM:: enlisting rm: " + rm);
		Vector<RM> v = activeRMs.get(txnID);
		if (v == null) {
			v = new Vector<RM>();
			v.add(rm);
			activeRMs.put(txnID, v);
		} else if (!v.contains(rm)) {
			v.add(rm);
			activeRMs.replace(txnID, v);
		}
	}

	// Remove all active RM's from activeRM list and remove txn from timeAlive list 
	private void delist(int txnID) {
		// make removal atomic
		activeRMs.computeIfPresent(txnID, (i,v)-> {
			timeAlive.remove(txnID);
			return null;
		});
		activeRMs.remove(txnID, null);
	}

	// Tell us the txn is alive
	public void ping(int txnID) {
		// Reset time to live for this txn
		new Thread(()->{
			try{
				timeAlive.replace(txnID, ttl);
			}
			catch(ConcurrentModificationException e){
				try {
					Thread.sleep(1);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				ping(txnID);
			}
		}).start();
	}
	
	protected void stopHeartbeatSweeper(){
		this.sweeper.interrupt();
	}
}
