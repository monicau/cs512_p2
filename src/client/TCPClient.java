package client;

import java.io.OutputStream;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import server.Messenger;
import server.RMItem;
import server.TCPServiceRequest;
import server.TimedExit;
import server.ws.ResourceManager;

public class TCPClient extends TCPServiceRequest implements ResourceManager{

	public TCPClient(Messenger middlewareIn, OutputStream middlewareOut) {
		super(middlewareIn, middlewareOut);
	}

	
	class FutureWaiter<T> implements Future<T>{
		BlockingQueue<T> element = new ArrayBlockingQueue<T>(1);
		public void offer(T t){
			try {
				element.put(t);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}
		@Override
		public boolean isCancelled() {
			return false;
		}
		@Override
		public boolean isDone() {
			return this.element.size()==1;
		}
		@Override
		public T get() throws InterruptedException, ExecutionException {
			return element.take();
		}
		@Override
		public T get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException,
				TimeoutException {
			return element.poll(timeout, unit);
		}
	}
	
	@Override
	public int getPrice(int id, String key) {
		throw new UnsupportedOperationException("Not for you, not for you");
	}

	@Override
	public boolean addFlight(int id, int flightNumber, int numSeats,
			int flightPrice) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.addFlight(id, flightNumber, numSeats, flightPrice, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean deleteFlight(int id, int flightNumber) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.deleteFlight(id, flightNumber,  v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public int queryFlight(int id, int flightNumber) {
		FutureWaiter<Integer> waiter = new FutureWaiter<>();
		this.queryFlight(id, flightNumber, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public int queryFlightPrice(int id, int flightNumber) {
		FutureWaiter<Integer> waiter = new FutureWaiter<>();
		this.queryFlightPrice(id, flightNumber, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public boolean addCars(int id, String location, int numCars, int carPrice) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.addCars(id, location, numCars, carPrice, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean deleteCars(int id, String location) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.deleteCars(id, location, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public int queryCars(int id, String location) {
		FutureWaiter<Integer> waiter = new FutureWaiter<>();
		this.queryCars(id, location, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public int queryCarsPrice(int id, String location) {
		FutureWaiter<Integer> waiter = new FutureWaiter<>();
		this.queryCarPrice(id, location, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.addRooms(id, location, numRooms, roomPrice, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean deleteRooms(int id, String location) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.deleteRooms(id, location, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public int queryRooms(int id, String location) {
		FutureWaiter<Integer> waiter = new FutureWaiter<>();
		this.queryRooms(id, location, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public int queryRoomsPrice(int id, String location) {
		FutureWaiter<Integer> waiter = new FutureWaiter<>();
		this.queryRoomPrice(id, location, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public int newCustomer(int id) {
		FutureWaiter<Integer> waiter = new FutureWaiter<>();
		this.newCustomer(id, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public boolean newCustomerId(int id, int customerId) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.newCustomerId(id, customerId, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean deleteCustomer(int id, int customerId) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.deleteCustomer(id, customerId, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public String queryCustomerInfo(int id, int customerId) {
		FutureWaiter<String> waiter = new FutureWaiter<>();
		this.queryCustomerInfo(id, customerId, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public boolean reserveItem(String reserveType, int id, int flightNumber,
			String location) {
		throw new UnsupportedOperationException("Not for you man");
	}

	@Override
	public boolean rmUnreserve(int id, String key, int reservationCount) {
		throw new UnsupportedOperationException("Can't have it");
	}

	@Override
	public boolean reserveFlight(int id, int customerId, int flightNumber) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.reserveFlight(id, customerId, flightNumber, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean reserveCar(int id, int customerId, String location) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.reserveCar(id, customerId, location, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean reserveRoom(int id, int customerId, String location) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.reserveRoom(id, customerId, location, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean reserveItinerary(int id, int customerId,
			Vector flightNumbers, String location, boolean car, boolean room) {
		FutureWaiter<Boolean> waiter = new FutureWaiter<>();
		this.reserveItinerary(id, customerId, flightNumbers, location, car, room, v->waiter.offer(v));
		try {
			return waiter.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public int start() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean commit(int transactionId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean abort(int transactionId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean shutdown() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean unlock(int txnID) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public RMItem readFromStorage(int id, String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void writeToStorage(int id, String key, RMItem value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public RMItem deleteFromStorage(int id, String key) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void removeTxn(int txnID) {
		// TODO Auto-generated method stub
	}

	@Override
	public void crash(String target) {
	}
	
	@Override
	public void selfDestruct() {
	}

	@Override
	public boolean votePhase(int transactionId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean decisionPhase(int transactionId, boolean commit) {
		// TODO Auto-generated method stub
		return false;
	}

}
