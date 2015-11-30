package TransactionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
	
	private int crashPoint;
	
	public TransactionManager(MiddlewareImpl middleware, ResourceManager flight, ResourceManager car, ResourceManager room) {
		activeRMs = new RMMap<Integer, Vector<RM>>();
		timeAlive = new RMMap<Integer, Integer>();
		txnCounter = new AtomicInteger();
		activeTransactions = new Vector<Integer>();
		crashPoint = -1;
		
		mw = middleware;
		proxyFlight = flight;
		proxyCar = car;
		proxyRoom = room;
		
		this.logger = new Logger2PC(Type.coordinator);
		
		recover();
	}
	
	private void recover() {
		// Go through coordinator log to see if there are any unfinished 2PC 
		try {
			File log = new File("coordinator/log.txt");
			if (!log.exists()) return;
			
			FileInputStream in = new FileInputStream(log);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String line = null;
			while ((line = br.readLine()) != null) {
				System.out.println("Read: " + line);
				StringTokenizer tokens = new StringTokenizer(line, ",");
				if (tokens.countTokens() == 1) {
					int txn = Integer.parseInt(tokens.nextToken());
					System.out.println("Log finds start of txn " + txn);
					
					// Read next line to see if this txn completed
					line = br.readLine();
					System.out.println("Next line: " + line);
					if (line == null || (new StringTokenizer(line, ",").countTokens() != 2)) {
						// Finish the 2PC for this transaction by sending abort 
						System.out.println("Detect unfinished 2PC protocol. Sending abort of txn " + txn);
						try {
							mw.abort(txn);
						} catch (Exception e) {
							// May receive invalid/aborted exception since it might have timed out at RM's
						}
						try {
							proxyFlight.abort(txn);
						} catch (Exception e) {
							// May receive invalid/aborted exception since it might have timed out at RM's
						}
						try {
							proxyCar.abort(txn);
						} catch (Exception e) {
							// May receive invalid/aborted exception since it might have timed out at RM's
						}
						try {
							proxyRoom.abort(txn);
						} catch (Exception e) {
							// May receive invalid/aborted exception since it might have timed out at RM's
						}
						
						if (line == null) return;
					} else {
						continue;
					}
				} 
			}
		} catch (IOException e) {
			System.out.println("Log file does not exist.  No coordinator recovery needed.");
		}
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
		System.out.println("TM:: Starting 2PC for txn "+txnID);    
		
		System.out.println("TM:: Logging start of 2PC");
		logger.log(Integer.toString(txnID));
		
		if (crashPoint == 1) mw.selfDestruct();
		
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
							if (crashPoint == 3) mw.selfDestruct();
						}
					} else {
						System.out.println("TM:: no RMs involved in this txn.  Committing nothing..");
					}
				} catch (Exception e) {
					// One of the RM's consider the transaction invalid/aborted 
					success = false;
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
		
		if (crashPoint == 8) return false;
		if (crashPoint == 4) mw.selfDestruct();
		
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
			
			if (crashPoint == 5) mw.selfDestruct();
			
			for (RM rm : rms) {
				switch (rm){
				case CAR:
					System.out.println("Sending decision to car.. ");
					proxyCar.decisionPhase(txnID, successful);
					break;
				case FLIGHT:
					System.out.println("Sending decision to flight.. ");
					proxyFlight.decisionPhase(txnID, successful);
					break;
				case ROOM:
					System.out.println("Sending decision to room.. ");
					proxyRoom.decisionPhase(txnID, successful);
					break;
				case CUSTOMER:
					System.out.println("Sending decision to mw.. ");
					mw.decisionPhase(txnID, successful);
					break;
				}
				if (crashPoint == 6) mw.selfDestruct();
			}
			//Remove rm's from activeRM 
			int index = activeTransactions.indexOf(txnID);
			activeTransactions.removeElementAt(index);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (crashPoint == 7) mw.selfDestruct();
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
	
	public void setCrashPoint(int crashPoint) {
		this.crashPoint = crashPoint;
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
