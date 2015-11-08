package server;

import java.util.Vector;

public class TransactionManager {
	public enum RM { FLIGHT, CAR, ROOM };
	private RMMap<Integer, Vector<RM>> activeRMs;
	private RMMap<Integer, Vector<ItemHistory>> txnHistory;
	private int txnCounter;
	
	public TransactionManager() {
		activeRMs = new RMMap<Integer, Vector<RM>>();
		txnHistory = new RMMap<Integer, Vector<ItemHistory>>();
		txnCounter = 0;
	}
	
	// Return a new txn ID
	synchronized public int start() {
		txnCounter++;
		return txnCounter;
	}
	
	public void abort() {
		
	}
	
	public void sendCommit(RM rm) {
		
	}
	
	public void sendAbort(RM rm) {
		
	}
	
	// Add RM to a txn
	public void enlist(int txnID, RM rm) {
		Vector<RM> v = activeRMs.get(txnID);
		if (v == null) {
			v = new Vector<RM>();
			v.add(rm);
			activeRMs.put(txnID, v);
		} else {
			v.add(rm);
			activeRMs.replace(txnID, v);
		}
	}
	
	// Methods for txnHistory
	
	public void removeTxn(int txnID) {
		txnHistory.remove(txnID);
	}
	
	public Vector<ItemHistory> getTxnHistory(int txnID) {
		return txnHistory.get(txnID);
	}
	
	public void setTxnHistory(int txnID, Vector<ItemHistory> v) {
		txnHistory.put(txnID, v);
	}
}
