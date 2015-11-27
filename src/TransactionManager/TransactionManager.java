package TransactionManager;

import java.io.File;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import server.Logger2PC;
import server.MiddlewareImpl;
import server.RMMap;
import server.ResourceManager;
import server.Logger2PC.Type;


public class TransactionManager {
	public enum RM { FLIGHT, CAR, ROOM, CUSTOMER };
	private RMMap<Integer, Vector<RM>> activeRMs;
	private Vector<Integer> activeTransactions;
	private AtomicInteger txnCounter;

	private MiddlewareImpl mw;
	private ResourceManager proxyFlight;
	private ResourceManager proxyCar;
	private ResourceManager proxyRoom;
	private int ttl; // time to live timeout
	private RMMap<Integer, Integer> timeAlive;// key=txnID, value=time alive

	private Thread sweeper;

	private Logger2PC logger;
	
	public TransactionManager(MiddlewareImpl middleware, ResourceManager flight, ResourceManager car, ResourceManager room) {
		activeRMs = new RMMap<Integer, Vector<RM>>();
		timeAlive = new RMMap<Integer, Integer>();
		txnCounter = new AtomicInteger();
		activeTransactions = new Vector<Integer>();
		
		mw = middleware;
		proxyFlight = flight;
		proxyCar = car;
		proxyRoom = room;
		
		this.logger = new Logger2PC(Type.coordinator);
	}
	
	public boolean isValidTransaction(int txnID) {
		return activeTransactions.contains(txnID);
	}
	
	public void setTxnCounter(int txn) {
		txnCounter.set(txn);
	}

	// Return a new txn ID
	synchronized public int start() {
		int counter = txnCounter.incrementAndGet();
		activeTransactions.add(counter);
		// Start timing this transaction
		timeAlive.put(counter, ttl);
		
		return counter;
	}
	synchronized public boolean commit(int txnID) throws InvalidTransactionException {
		System.out.println("TM:: Committing transaction "+txnID);    
		
		// Remove txn history on other resource managers and unlock locks
		
		// start timer
		Vector<RM> rms = activeRMs.get(txnID);
		Callable<Boolean> child = new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				boolean success = true;
				try {
					if (rms != null) {
						for (RM rm : rms) {
							switch (rm) {
							case CUSTOMER:
								System.out.println("TM:: sending vote request to customer");
								success &= mw.prepare(txnID);
								break;
							case FLIGHT:
								System.out.println("TM:: sending vote request to flight");
								success &= proxyFlight.prepare(txnID);
								break;
							case CAR:
								System.out.println("TM:: sending vote request to car");
								success &= proxyCar.prepare(txnID);
								break;
							case ROOM:
								System.out.println("TM:: sending vote request to room");
								success &= proxyRoom.prepare(txnID);
								break;
							default:
								break;
							}
						}
					} else {
						System.out.println("TM:: no RMs involved in this txn.  Committing nothing..");
					}
				} catch (Exception e) {
					throw new InvalidTransactionException();
				}
				System.out.println("TM:: vote decision: " + success); 
				return success;
			}
		};
		ExecutorService pool = Executors.newFixedThreadPool(1);
		Future<Boolean> submit = pool.submit(child);
		Boolean successful = false;
		try{
			successful = submit.get(1000, TimeUnit.MILLISECONDS);
		}
		catch(Exception e){
			e.printStackTrace();
		}
		pool.shutdownNow();
		try {
			if(successful == null ){
				// Timeout before receiving all votes. Send out abort
				System.out.println("TM:: timeout before receiving all votes. Sending abort..");
				this.logger.log(txnID+","+false);
				for (RM rm : rms) {
					switch (rm){
					case CAR:
						System.out.println("TM:: Aborting car");
						proxyCar.abort(txnID);
						break;
					case FLIGHT:
						System.out.println("TM:: Aborting flight");
						proxyFlight.abort(txnID);
						break;
					case ROOM:
						System.out.println("TM:: Aborting room");
						proxyRoom.abort(txnID);
						break;
					case CUSTOMER:
						System.out.println("TM:: Aborting customer");
						mw.abortCustomer(txnID);
						break;
					}
				}
				return false;
			}
			System.out.println("TM:: sending decision: " + successful);
			this.logger.log(txnID+","+successful);
			for (RM rm : rms) {
				switch (rm){
				case CAR:
					proxyCar.decisionPhase(txnID, successful);
					break;
				case FLIGHT:
					proxyFlight.decisionPhase(txnID, successful);
					break;
				case ROOM:
					proxyRoom.decisionPhase(txnID, successful);
					break;
				case CUSTOMER:
					mw.decisionPhase(txnID, successful);
					break;
				}
			}
			//Remove rm's from activeRM 
			int index = activeTransactions.indexOf(txnID);
			activeTransactions.removeElementAt(index);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return successful;
	}   

	public boolean abort(int txnID) throws InvalidTransactionException {
		System.out.println("TM:: Aborting a transaction " + txnID);      
		System.out.println("TM:: sending abort to resource managers..");
		boolean success = true;
		// Remove txn history on other resource managers and unlock locks
		Vector<RM> rms = activeRMs.get(txnID);
		try {
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
		} catch (Exception e) {
			throw new InvalidTransactionException();
		}
		System.out.println("TM:: Unlock all locks held by this transaction: " + success);     
		//Remove rm's from activeRM 
		delist(txnID);
		int index = activeTransactions.indexOf(txnID);
		activeTransactions.removeElementAt(index);
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
//	public void ping(int txnID) {
//		// Reset time to live for this txn
//		new Thread(()->{
//			try{
//				timeAlive.replace(txnID, ttl);
//			}
//			catch(ConcurrentModificationException e){
//				try {
//					Thread.sleep(1);
//				} catch (Exception e1) {
//					e1.printStackTrace();
//				}
//				ping(txnID);
//			}
//		}).start();
//	}
	
//	public void stopHeartbeatSweeper(){
//		this.sweeper.interrupt();
//	}
}
