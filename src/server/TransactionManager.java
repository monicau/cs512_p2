package server;

import java.util.Vector;

public class TransactionManager {
	public enum RM { FLIGHT, CAR, ROOM };
	private RMMap<Integer, Vector<RM>> activeRMs;
	private RMMap<Integer, Vector<ItemHistory>> txnHistory;
	private int txnCounter;
	private MiddlewareImpl mw;
	private ResourceManager proxyFlight;
	private ResourceManager proxyCar;
	private ResourceManager proxyRoom;
	
	public TransactionManager(MiddlewareImpl middleware, ResourceManager flight, ResourceManager car, ResourceManager room) {
		activeRMs = new RMMap<Integer, Vector<RM>>();
		txnHistory = new RMMap<Integer, Vector<ItemHistory>>();
		txnCounter = 0;
		mw = middleware;
		proxyFlight = flight;
		proxyCar = car;
		proxyRoom = room;
	}
	
	// Return a new txn ID
	synchronized public int start() {
		txnCounter++;
		System.out.println("Calling mw... "+ mw.talk());
		System.out.println("Calling proxy flight..." + proxyFlight.talk());;
		return txnCounter;
	}
	public boolean commit(int txnID) {                                                   
		System.out.println("TM:: Committing transaction "+txnID);                       
		// Delete this txn from txnHistory since we know we won't abort anymore         
		removeTxn(txnID);                                                               
		//TODO: remove txn history on other resource managers when we work on distributed version 
		proxyFlight.removeTxn(txnID);
		proxyCar.removeTxn(txnID);
		proxyRoom.removeTxn(txnID);
		// Unlock all locks that this txn has locks on
		boolean r1 = mw.unlock(txnID);    
		boolean r2 = proxyFlight.unlock(txnID);
		boolean r3 = proxyCar.unlock(txnID);
		boolean r4 = proxyRoom.unlock(txnID);
		System.out.println("TM:: Unlock all locks held by this transaction. customer:" + r1 + ", flight:" + r2 + ", car:" + r3 + ", room:" + r4);     
		return r1 && r2 && r3 && r4;                                                                       
	}   
	
	public boolean abort(int txnID) {
        System.out.println("TM:: Aborting a transaction " + txnID);      
        // Revert changes                                                
		Vector<ItemHistory> history = getTxnHistory(txnID);
		if (history != null) {
			System.out.println("TM:: Reverting changes...");
			for (ItemHistory item : history) {
				if (item.getAction() == ItemHistory.Action.ADDED) {
					// Delete item from storage
					System.out.println("TM:: Deleting added customer.");
					mw.deleteFromStorage(txnID,
							((Customer) item.getItem()).getKey());
				} else if (item.getAction() == ItemHistory.Action.DELETED) {
					// Add back to storage
					System.out.println("TM:: Adding a deleted customer.");
					mw.writeToStorage(txnID,
							((Customer) item.getItem()).getKey(),
							((Customer) item.getItem()));
				} else {
					// Item was updated. Revert back to old version
					System.out
							.println("TM:: Reverting customer to its old stats");
					mw.deleteFromStorage(txnID,
							((Customer) item.getItem()).getKey());
					// Remove reservation from customer object
					Customer c = (Customer) item.getItem();
					String key = item.getReservedItemKey();
					System.out.println("TM::Abort is unreserving " + key);
					c.unreserve(key);
					// Save updated customer object to storage
					mw.writeToStorage(txnID, c.getKey(), c);
				}
			}
		}                                                               
        removeTxn(txnID);                                                
        // TODO: abort on other resource managers                        
                                                                         
        // Unlock all locks that this txn has locks on   
        boolean r = mw.unlock(txnID);                                    
        System.out.println("TM:: Unlock all held locks:" + r);           
        return true;
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
