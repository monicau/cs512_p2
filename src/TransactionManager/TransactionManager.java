package TransactionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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
	
	private Map<Integer, Boolean> oldTransactions = new ConcurrentHashMap<>();
	
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
		
		recover2();
		
		// start a server thread for recovery of rms
		// using static port 9999
		
		try {
			ServerSocket socket =  new ServerSocket(9999);
			
			Thread acceptor = new Thread(() -> {
				while(true){
					try {
						Socket incoming = socket.accept();
						Thread recoveryHelper = new Thread(()->{
							try {
								BufferedReader br = new BufferedReader(new InputStreamReader(incoming.getInputStream()));
								PrintWriter writer = new PrintWriter(incoming.getOutputStream());
								String line = br.readLine();
								Boolean data = oldTransactions.get(Integer.parseInt(line));
								writer.write(""+data);
								writer.close();
								br.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						});
						recoveryHelper.start();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
			});
			acceptor.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// fixed infinite loop
	private void recover2(){
		Path logPath = null;
		try {
			logPath = Paths.get("coordinator/log.txt");
		} catch (InvalidPathException e) {
//			e.printStackTrace();
		}
		try {
			List<String> allLines = Files.readAllLines(logPath);
			int n = allLines.size();
			for (int i = 0; i < n; i++) {
				String line = allLines.get(i);
				String[] split = line.split(",");
				int txn = Integer.parseInt(split[0]);
				System.out.println("Log finds start of txn " + txn);

				switch(split.length){
				case 1:
					// Check if next line is the decision for this transaction
					if (i+1 < n) {
						String nextLine = i<n ? allLines.get(i+1) : "";
						String[] nextSplit = nextLine.split(",");
						switch(nextSplit.length){
						case 2:
							// Check if this next line contains our txn id
							// if it's ours, do nothing, it will get handled on next iteration
//							System.out.println("Next line has 2 arguments. Comparing id: " + nextSplit[0] + " vs " + split[0]);
							if (nextSplit[0].equals(split[0])) break;
						default:
							// Transaction with undecided outcome
							// Finish the 2PC for this transaction by sending abort 
							System.out.println("Detect unfinished 2PC protocol. Sending abort of txn " + txn);
							tryAbort(mw, txn);
							tryAbort(proxyFlight, txn);
							tryAbort(proxyCar, txn);
							tryAbort(proxyRoom, txn);
							if (nextLine.isEmpty()) return;
							break;
						}
					} else {
						// Next line doesn't exist - 2PC never finished
						// Send abort
						System.out.println("Detect unfinished 2PC protocol. Sending abort of txn " + txn);
						tryAbort(mw, txn);
						tryAbort(proxyFlight, txn);
						tryAbort(proxyCar, txn);
						tryAbort(proxyRoom, txn);
						return;
					}
					break;
				case 2:
					boolean decision = Boolean.parseBoolean(split[1]);
					tryDecisionPhase(mw, txn, decision);
					tryDecisionPhase(proxyFlight, txn, decision);
					tryDecisionPhase(proxyCar, txn, decision);
					tryDecisionPhase(proxyRoom, txn, decision);
					break;
				default:
					throw new IllegalStateException("There is more than 2 values in the log entry");
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void tryAbort(ResourceManager rm, int txn){
		try {
			rm.abort(txn);
		} catch (Exception e) {
			// May receive invalid/aborted exception since it might have timed out at RM's
		}
	}
	private void tryAbort(MiddlewareImpl mw, int txn){
		try {
			mw.abort(txn);
		} catch (Exception e) {
			// May receive invalid/aborted exception since it might have timed out at RM's
		}
	}	
	private void tryDecisionPhase(ResourceManager rm, int txn, boolean decision){
		try {
			rm.decisionPhase(txn, decision);
		} catch (Exception e) {
			// May receive invalid/aborted exception since it might have timed out at RM's
		}
	}
	private void tryDecisionPhase(MiddlewareImpl mw, int txn, boolean decision){
		try {
			mw.decisionPhase(txn, decision);
		} catch (Exception e) {
			// May receive invalid/aborted exception since it might have timed out at RM's
		}
	}
	
	private void recover() {
		// Go through coordinator log to see if there are any unfinished 2PC 
		try {
			File log = new File("coordinator/log.txt");
			if (!log.exists()) return;
			
			FileInputStream in = new FileInputStream(log);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String line = null;
			StringTokenizer tokens;
			line = br.readLine();
			while (line != null) {
				System.out.println("Coordinator read: " + line);
				tokens = new StringTokenizer(line, ",");
				if (tokens.countTokens() == 1) {
					int txn = Integer.parseInt(tokens.nextToken());
					System.out.println("Log finds start of txn " + txn);
					
					// Read next line to see if this txn completed
					line = br.readLine();
					System.out.println("Next line: " + line);
					if (line == null || (tokens = new StringTokenizer(line, ",")).countTokens() != 2) {
						// Transaction with undecided outcome
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
					}
					else if (tokens.countTokens() == 2) {
						System.out.println("2 tokens");
						// Coordinator has either sent decision to all resource managers, to some, or none
						// Resend decision
						int txn2 = Integer.parseInt(tokens.nextToken());
						if (txn2 != txn) System.out.println("Transaction logging out of order! What!");
						boolean decision = Boolean.parseBoolean(tokens.nextToken());
						try {
							mw.decisionPhase(txn, decision);
						} catch (Exception e) {
							// May receive invalid/aborted exception since it might have timed out at RM's
						}
						try {
							proxyFlight.decisionPhase(txn, decision);
						} catch (Exception e) {
							// May receive invalid/aborted exception since it might have timed out at RM's
						}
						try {
							proxyCar.decisionPhase(txn, decision);
						} catch (Exception e) {
							// May receive invalid/aborted exception since it might have timed out at RM's
						}
						try {
							proxyRoom.decisionPhase(txn, decision);
						} catch (Exception e) {
							// May receive invalid/aborted exception since it might have timed out at RM's
						}
						// Read next line for next iteration
						line = br.readLine();
						System.out.println("Next line :"+line);
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
					System.out.println("TM:: caught an error instead of a vote answer: ");
					e.printStackTrace();
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
					oldTransactions.put( txnID, successful);
					proxyCar.decisionPhase(txnID, successful);
					break;
				case FLIGHT:
					System.out.println("Sending decision to flight.. ");
					oldTransactions.put( txnID, successful);
					proxyFlight.decisionPhase(txnID, successful);
					break;
				case ROOM:
					System.out.println("Sending decision to room.. ");
					oldTransactions.put( txnID, successful);
					proxyRoom.decisionPhase(txnID, successful);
					break;
				case CUSTOMER:
					System.out.println("Sending decision to mw.. ");
					oldTransactions.put( txnID, successful);
					mw.decisionPhase(txnID, successful);
					break;
				}
				if (crashPoint == 6) mw.selfDestruct();
			}
			//Remove rm's from activeRM 
			int index = activeTransactions.indexOf(txnID);
			if (index != -1) activeTransactions.removeElementAt(index);
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
		if (index != -1) activeTransactions.removeElementAt(index);
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
