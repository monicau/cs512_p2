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

		Vector<RM> vector = activeRMs.get(0);

		sweeper = new Thread(() ->{
			while(!Thread.interrupted()){
				Iterator<Entry<Integer, Integer>> it = timeAlive.entrySet().iterator();
				while(it.hasNext()){
					Entry<Integer, Integer> livedFor = it.next();
					int txnID = livedFor.getKey();
					int timealive = livedFor.getValue();
					if(timealive > ttl){
						boolean success = true;
						for (RM rm : vector) {
							switch (rm) {
							case CUSTOMER:
								System.out.println("TM:: clearing txn history and unlocking customer");
								mw.removeTxn(txnID);
								success = mw.unlock(txnID) && success;
								break;
							case FLIGHT:
								System.out.println("TM:: clearing txn history and unlocking flight");
								proxyFlight.removeTxn(txnID);
								success = proxyFlight.unlock(txnID) && success;
								break;
							case CAR:
								System.out.println("TM:: clearing txn history and unlocking car");
								proxyCar.removeTxn(txnID);
								success = proxyCar.unlock(txnID) && success;
								break;
							case ROOM:
								System.out.println("TM:: clearing txn history and unlocking room");
								proxyRoom.removeTxn(txnID);
								success = proxyRoom.unlock(txnID) && success;
								break;
							default:
								throw new IllegalStateException("A new state is detected, don't know what to do with it");
							}
						}
						System.out.println("TM:: Unlock all locks held by this transaction: " + success); 
						delist(txnID);
					}
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
				if (rm == RM.CUSTOMER) {
					System.out.println("TM:: clearing txn history and unlocking customer");
					mw.removeTxn(txnID);
					success = mw.unlock(txnID) && success;
				} else if (rm == RM.FLIGHT) {
					System.out.println("TM:: clearing txn history and unlocking flight");
					proxyFlight.removeTxn(txnID);
					success = proxyFlight.unlock(txnID) && success;
				} else if (rm == RM.CAR) {
					System.out.println("TM:: clearing txn history and unlocking car");
					proxyCar.removeTxn(txnID);
					success = proxyCar.unlock(txnID) && success;
				} else if (rm == RM.ROOM) {
					System.out.println("TM:: clearing txn history and unlocking room");
					proxyRoom.removeTxn(txnID);
					success = proxyRoom.unlock(txnID) && success;
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
				if (rm == RM.CUSTOMER) {
					mw.abortCustomer(txnID);
					success = mw.unlock(txnID) && success;
				} else if (rm == RM.FLIGHT) {
					proxyFlight.abort(txnID);
					success = proxyFlight.unlock(txnID) && success;
				} else if (rm == RM.CAR) {
					proxyCar.abort(txnID);
					success = proxyCar.unlock(txnID) && success;
				} else if (rm == RM.ROOM) {
					proxyRoom.abort(txnID);
					success = proxyRoom.unlock(txnID) && success;
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
		activeRMs.remove(txnID);
		timeAlive.remove(txnID);
	}

	// Tell us the txn is alive
	public void ping(int txnID) {
		// Reset time to live for this txn
		new Thread(()->{
			try{
				timeAlive.replace(txnID, ttl);
			}
			catch(ConcurrentModificationException e){
				Thread.sleep(1);
				ping(txnID);
			}
		}).start();
	}
}
