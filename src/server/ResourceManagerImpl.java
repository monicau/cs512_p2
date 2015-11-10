// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.jws.WebService;

import server.ItemHistory.Action;
import server.ItemHistory.ItemType;
import lockmanager.DeadlockException;
import lockmanager.LockManager;

import com.google.gson.Gson;

@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImpl implements server.ws.ResourceManager {
	private String MW_LOCATION = "localhost";
	private RMMap<Integer, Vector<ItemHistory>> txnHistory;
	private LockManager lm;

	private AtomicInteger lastTrxnID = new AtomicInteger(0);
	
	boolean useWebService;

	AtomicReference<Messenger> messenger_ref = new AtomicReference<>();

	public ResourceManagerImpl() {
		lm = new LockManager();
		txnHistory = new RMMap<Integer, Vector<ItemHistory>>();
		// Determine if we are using web services or tcp
		try {
			BufferedReader reader = new BufferedReader(new FileReader(new File(
					"serviceType.txt")));
			try {
				String line = reader.readLine();
				reader.close();
				if (line.equals("ws")) {
					useWebService = true;
				} else {
					useWebService = false;
					reader = new BufferedReader(new FileReader(new File(
							"config.txt")));
					MW_LOCATION = reader.readLine();
					MW_LOCATION = reader.readLine();
				}
			} catch (IOException e) {
				Trace.info("ERROR: IOException, cannot read serviceType.txt");
			}
		} catch (FileNotFoundException e) {
			Trace.info("ERROR: Cannot find serviceType.txt");
		}
		if (!useWebService) {
			new Thread(
					() -> {
						try {
							getPort(port -> {
								try {
									System.out
											.println("Making new messenger at port "
													+ port + " for RM");
									Messenger msger = new Messenger(port);
									messenger_ref.set(msger);
									Trace.info("Made messenger for RM");
									msger.onMessage = (message, socket,
											outputstream) -> {
										Trace.info("RM got message: "
												+ message);
										// Messages have form
										// method_name(type1,type2,...,typen)var1,var2,...,varn
										int split1 = message.indexOf('(');
										int split2 = message.indexOf(')');
										String methodname = message.substring(
												0, split1);

										Trace.info("Resolving request "
												+ methodname
												+ " locally in middleware");
										String paramtypes = message.substring(
												split1 + 1, split2);
										String varsvalues = message
												.substring(split2 + 1);
										String[] splittedParams = paramtypes
												.split(",");
										Class<?>[] types = new Class<?>[splittedParams.length];
										for (int i = 0; i < splittedParams.length; i++) {
											String param = splittedParams[i];
											try {
												types[i] = param.equals("int") ? int.class
														: param.equals("boolean") ? boolean.class
																: param.equals("String") ? String.class
																		: Class.forName(param);
											} catch (Exception e) {
												e.printStackTrace();
											}
										}
										Method method = null;
										try {
											method = this.getClass().getMethod(
													methodname, types);
										} catch (Exception e) {
											e.printStackTrace();
										}
										varsvalues = "[" + varsvalues + "]";
										Object[] vars = new Gson().fromJson(
												varsvalues, Object[].class);
										for (int i = 0; i < vars.length; i++) {
											try {
												vars[i] = types[i]
														.cast(vars[i]);
											} catch (Exception e) {
												vars[i] = (int) (((Double) vars[i])
														.doubleValue());
											}
											System.out.print(vars[i] + " ");
										}
										System.out.println();
										Object result = null;
										try {
											result = method.invoke(this, vars);
										} catch (Exception e) {
											e.printStackTrace();
										}
										try (Socket toMW = new Socket(
												MW_LOCATION, 9090)) {
											OutputStream os = toMW
													.getOutputStream();
											PrintWriter writer = new PrintWriter(
													os, true);
											String response = methodname + ":"
													+ result;
											System.out
													.println("Returning result "
															+ response);
											writer.println(response);
										} catch (Exception e) {
											e.printStackTrace();
										}
									};
									msger.start();
								} catch (Exception e) {
									e.printStackTrace();
								}
							});
						} catch (Exception e) {
							e.printStackTrace();
						}
					}).start();
		}
	}

	void getPort(Consumer<Integer> onGetPort) throws InterruptedException {
		Trace.info("Trying to get a port");
		try (Socket socket = new Socket(MW_LOCATION, 9090)) {
			try {
				OutputStream oos = socket.getOutputStream();
				PrintWriter writer = new PrintWriter(oos, true);
				writer.println("[port?]");
				InputStream is = socket.getInputStream();
				BufferedReader br = new BufferedReader(
						new InputStreamReader(is));
				String message = br.readLine();
				Trace.info("Received port " + message);
				onGetPort.accept(Integer.parseInt(message));
				return;
			} catch (Exception e) {
				System.out.println(e);
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			Thread.sleep(1000);
			getPort(onGetPort);
		}
	}

	protected RMMap m_itemHT = new RMMap<>();

	// Basic operations on RMItem //

	// Read a data item.
	private RMItem readData(int id, String key) {
		synchronized (m_itemHT) {
			return (RMItem) m_itemHT.get(key);
		}
	}

	private ReservableItem getReservableItem(int id, String key) {
		synchronized (m_itemHT) {
			return (ReservableItem) m_itemHT.get(key);
		}
	}

	// Write a data item.
	private void writeData(int id, String key, RMItem value) {
		synchronized (m_itemHT) {
			m_itemHT.put(key, value);
		}
	}

	// Remove the item out of storage.
	protected RMItem removeData(int id, String key) {
		synchronized (m_itemHT) {
			return (RMItem) m_itemHT.remove(key);
		}
	}

	// Basic operations on ReservableItem //

	// Delete the entire item.
	protected boolean deleteItem(int id, String key) {
		Trace.info("RM::deleteItem(" + id + ", " + key + ") called.");
		// Synchronize delete in case someone reserves same item while we are
		// trying to delete it
		ReservableItem curObj = (ReservableItem) readData(id, key);
		synchronized (curObj) {
			// Check if there is such an item in the storage.
			if (curObj == null) {
				Trace.warn("RM::deleteItem(" + id + ", " + key + ") failed: "
						+ " item doesn't exist.");
				return false;
			} else {
				if (curObj.getReserved() == 0) {
					// Add to txn history
					ItemType myType;
					if (curObj.getKey().contains("flight")) {
						myType = ItemType.FLIGHT;
					} else if (curObj.getKey().contains("car")) {
						myType = ItemType.CAR;
					} else {
						myType = ItemType.ROOM;
					}
					ItemHistory history = new ItemHistory(myType, ItemHistory.Action.DELETED, curObj, curObj.getKey());
					addTxnHistory(id, history);
					// Delete from storage
					removeData(id, curObj.getKey());
					Trace.info("RM::deleteItem(" + id + ", " + key + ") OK.");
					return true;
				} else {
					Trace.info("RM::deleteItem(" + id + ", " + key
							+ ") failed: " + "some customers have reserved it.");
					return false;
				}
			}
		}
	}

	// Query the number of available seats/rooms/cars.
	protected int queryNum(int id, String key) {
		Trace.info("RM::queryNum(" + id + ", " + key + ") called.");
		ReservableItem curObj = (ReservableItem) readData(id, key);
		int value = 0;
		if (curObj != null) {
			value = curObj.getCount();
		}
		Trace.info("RM::queryNum(" + id + ", " + key + ") OK: " + value);
		return value;
	}

	// Query the price of an item.
	protected int queryPrice(int id, String key) {
		Trace.info("RM::queryPrice(" + id + ", " + key + ") called.");
		ReservableItem curObj = (ReservableItem) readData(id, key);
		int value = 0;
		if (curObj != null) {
			value = curObj.getPrice();
		}
		Trace.info("RM::queryPrice(" + id + ", " + key + ") OK: $" + value);
		return value;
	}

	// Public version
	public int getPrice(int id, String key) {
		return queryPrice(id, key);
	}

	// Flight operations //

	// Create a new flight, or add seats to existing flight.
	// Note: if flightPrice <= 0 and the flight already exists, it maintains
	// its current price.
	@Override
	synchronized public boolean addFlight(int id, int flightNumber,
			int numSeats, int flightPrice) throws DeadlockException {
		Trace.info("RM::addFlight(" + id + ", " + flightNumber + ", $"
				+ flightPrice + ", " + numSeats + ") called.");
		lm.Lock(id, Flight.getKey(flightNumber), LockManager.WRITE);
		
		Flight curObj = (Flight) readData(id, Flight.getKey(flightNumber));
		
		ItemHistory history;
		
		if (curObj == null) {
			// Doesn't exist; add it.
			curObj = new Flight(flightNumber, numSeats, flightPrice);
			writeData(id, curObj.getKey(), curObj);
			Trace.info("RM::addFlight(" + id + ", " + flightNumber + ", $"
					+ flightPrice + ", " + numSeats + ") OK.");
			history = new ItemHistory(ItemType.FLIGHT, ItemHistory.Action.ADDED, curObj, curObj.getKey());

		} else {
			int oldCount = curObj.getCount();
			int oldPrice = curObj.getPrice();
			int oldReserved = curObj.getReserved();
			
			history = new ItemHistory(ItemType.FLIGHT, ItemHistory.Action.UPDATED, curObj, curObj.getKey(), oldCount, oldPrice, oldReserved);

			// Add seats to existing flight and update the price.
			curObj.setCount(curObj.getCount() + numSeats);
			if (flightPrice > 0) {
				curObj.setPrice(flightPrice);
			}
			writeData(id, curObj.getKey(), curObj);
			Trace.info("RM::addFlight(" + id + ", " + flightNumber + ", $"
					+ flightPrice + ", " + numSeats + ") OK: " + "seats = "
					+ curObj.getCount() + ", price = $" + flightPrice);
		}
		// Add to txn history
		addTxnHistory(id, history);
		return (true);
	}

	@Override
	public boolean deleteFlight(int id, int flightNumber) throws DeadlockException {
		lm.Lock(id, Flight.getKey(flightNumber), LockManager.WRITE);
		return deleteItem(id, Flight.getKey(flightNumber));
	}

	// Returns the number of empty seats on this flight.
	@Override
	public int queryFlight(int id, int flightNumber) throws DeadlockException {
		lm.Lock(id, Flight.getKey(flightNumber), LockManager.READ);
		return queryNum(id, Flight.getKey(flightNumber));
	}

	// Returns price of this flight.
	public int queryFlightPrice(int id, int flightNumber) throws DeadlockException {
		lm.Lock(id, Flight.getKey(flightNumber), LockManager.READ);
		return queryPrice(id, Flight.getKey(flightNumber));
	}

	/*
	 * // Returns the number of reservations for this flight. public int
	 * queryFlightReservations(int id, int flightNumber) {
	 * Trace.info("RM::queryFlightReservations(" + id + ", #" + flightNumber +
	 * ") called."); RMInteger numReservations = (RMInteger) readData(id,
	 * Flight.getNumReservationsKey(flightNumber)); if (numReservations == null)
	 * { numReservations = new RMInteger(0); }
	 * Trace.info("RM::queryFlightReservations(" + id + ", #" + flightNumber +
	 * ") = " + numReservations); return numReservations.getValue(); }
	 */

	/*
	 * // Frees flight reservation record. Flight reservation records help us //
	 * make sure we don't delete a flight if one or more customers are //
	 * holding reservations. public boolean freeFlightReservation(int id, int
	 * flightNumber) { Trace.info("RM::freeFlightReservations(" + id + ", " +
	 * flightNumber + ") called."); RMInteger numReservations = (RMInteger)
	 * readData(id, Flight.getNumReservationsKey(flightNumber)); if
	 * (numReservations != null) { numReservations = new RMInteger( Math.max(0,
	 * numReservations.getValue() - 1)); } writeData(id,
	 * Flight.getNumReservationsKey(flightNumber), numReservations);
	 * Trace.info("RM::freeFlightReservations(" + id + ", " + flightNumber +
	 * ") OK: reservations = " + numReservations); return true; }
	 */

	// Car operations //

	// Create a new car location or add cars to an existing location.
	// Note: if price <= 0 and the car location already exists, it maintains
	// its current price.
	@Override
	synchronized public boolean addCars(int id, String location, int numCars,
			int carPrice) throws DeadlockException {
		Trace.info("RM::addCars(" + id + ", " + location + ", " + numCars
				+ ", $" + carPrice + ") called.");
		lm.Lock(id, Car.getKey(location), LockManager.WRITE);
		Car curObj = (Car) readData(id, Car.getKey(location));
		
		ItemHistory history;
		if (curObj == null) {
			// Doesn't exist; add it.
			curObj = new Car(location, numCars, carPrice);
			writeData(id, curObj.getKey(), curObj);
			Trace.info("RM::addCars(" + id + ", " + location + ", " + numCars
					+ ", $" + carPrice + ") OK.");
			
			history = new ItemHistory(ItemType.CAR, ItemHistory.Action.ADDED, curObj, curObj.getKey());
		} else {
			int oldCount = curObj.getCount();
			int oldPrice = curObj.getPrice();
			int oldReserved = curObj.getReserved();
			
			history = new ItemHistory(ItemType.CAR, ItemHistory.Action.UPDATED, curObj, curObj.getKey(), oldCount, oldPrice, oldReserved);
			
			// Add count to existing object and update price.
			curObj.setCount(curObj.getCount() + numCars);
			if (carPrice > 0) {
				curObj.setPrice(carPrice);
			}
			writeData(id, curObj.getKey(), curObj);
			Trace.info("RM::addCars(" + id + ", " + location + ", " + numCars
					+ ", $" + carPrice + ") OK: " + "cars = "
					+ curObj.getCount() + ", price = $" + carPrice);
		}
		// Add to txn history
		addTxnHistory(id, history);
		return (true);
	}

	// Delete cars from a location.
	@Override
	public boolean deleteCars(int id, String location) throws DeadlockException {
		lm.Lock(id, Car.getKey(location), LockManager.WRITE);
		return deleteItem(id, Car.getKey(location));
	}

	// Returns the number of cars available at a location.
	@Override
	public int queryCars(int id, String location) throws DeadlockException {
		lm.Lock(id, Car.getKey(location), LockManager.READ);
		return queryNum(id, Car.getKey(location));
	}

	// Returns price of cars at this location.
	@Override
	public int queryCarsPrice(int id, String location) throws DeadlockException {
		lm.Lock(id, Car.getKey(location), LockManager.READ);
		return queryPrice(id, Car.getKey(location));
	}

	// Room operations //

	// Create a new room location or add rooms to an existing location.
	// Note: if price <= 0 and the room location already exists, it maintains
	// its current price.
	@Override
	synchronized public boolean addRooms(int id, String location, int numRooms,
			int roomPrice) throws DeadlockException {
		Trace.info("RM::addRooms(" + id + ", " + location + ", " + numRooms
				+ ", $" + roomPrice + ") called.");
		lm.Lock(id, Room.getKey(location), LockManager.WRITE);
		Room curObj = (Room) readData(id, Room.getKey(location));
		ItemHistory history;
		if (curObj == null) {
			// Doesn't exist; add it.
			curObj = new Room(location, numRooms, roomPrice);
			writeData(id, curObj.getKey(), curObj);
			Trace.info("RM::addRooms(" + id + ", " + location + ", " + numRooms
					+ ", $" + roomPrice + ") OK.");
			history = new ItemHistory(ItemType.ROOM, ItemHistory.Action.ADDED, curObj, curObj.getKey());
		} else {
			int oldCount = curObj.getCount();
			int oldPrice = curObj.getPrice();
			int oldReserved = curObj.getReserved();
			
			history = new ItemHistory(ItemType.ROOM, ItemHistory.Action.UPDATED, curObj, curObj.getKey(), oldCount, oldPrice, oldReserved);
			
			// Add count to existing object and update price.
			curObj.setCount(curObj.getCount() + numRooms);
			if (roomPrice > 0) {
				curObj.setPrice(roomPrice);
			}
			writeData(id, curObj.getKey(), curObj);
			Trace.info("RM::addRooms(" + id + ", " + location + ", " + numRooms
					+ ", $" + roomPrice + ") OK: " + "rooms = "
					+ curObj.getCount() + ", price = $" + roomPrice);
		}
		// Add to txn history
		
		addTxnHistory(id, history);
		return (true);
	}

	// Delete rooms from a location.
	@Override
	public boolean deleteRooms(int id, String location) throws DeadlockException {
		lm.Lock(id, Room.getKey(location), LockManager.WRITE);
		return deleteItem(id, Room.getKey(location));
	}

	// Returns the number of rooms available at a location.
	@Override
	public int queryRooms(int id, String location) throws DeadlockException {
		lm.Lock(id, Room.getKey(location), LockManager.READ);
		return queryNum(id, Room.getKey(location));
	}

	// Returns room price at this location.
	@Override
	public int queryRoomsPrice(int id, String location) throws DeadlockException {
		lm.Lock(id, Room.getKey(location), LockManager.READ);
		return queryPrice(id, Room.getKey(location));
	}

	// Customer operations //

	// Method for middleware only.
	@Override
	public int newCustomer(int id) {
		return -1;
	}

	// Method for middleware only. @Override
	public boolean newCustomerId(int id, int customerId) {
		return false;
	}

	// Method for middleware only.
	@Override
	public boolean deleteCustomer(int id, int customerId) {
		return false;
	}

	// Method for middleware only.
	public RMMap getCustomerReservations(int id, int customerId) {
		return null;
	}

	// Method for middleware only.
	@Override
	public String queryCustomerInfo(int id, int customerId) {
		return "No customer information on this server.";
	}

	/**
	 * 
	 * @param reserveType
	 *            flight, car or room
	 * @param id
	 *            id
	 * @param flightNumber
	 *            flight number if applicable
	 * @param location
	 *            car or room location if applicable
	 * @return true if success, else false
	 * @throws DeadlockException
	 */
	// Add reservation
	@Override
	public boolean reserveItem(String reserveType, int id, int flightNumber,
			String location) throws DeadlockException {
		String key = null;
		ItemType itemType;
		if (reserveType.toLowerCase().equals("flight")) {
			location = String.valueOf(flightNumber);
			key = Flight.getKey(flightNumber);
			itemType = ItemHistory.ItemType.FLIGHT;
		} else if (reserveType.toLowerCase().equals("car")) {
			key = Car.getKey(location);
			itemType = ItemHistory.ItemType.CAR;
		} else if (reserveType.toLowerCase().equals("room")) {
			key = Room.getKey(location);
			itemType = ItemHistory.ItemType.ROOM;
		} else {
			return false;
		}
		if (key == null)
			return false;
		// Write lock item
		lm.Lock(id, key, LockManager.WRITE);
		// Check if the item is available.
		ReservableItem item = (ReservableItem) readData(id, key);
		if (item == null) {
			Trace.warn("RM::rmReserve failed: item doesn't exist.");
			return false;
		} else if (item.getCount() == 0) {
			Trace.warn("RM::rmReserve failed: no more items.");
			return false;
		} else {
			// Do reservation.
			// Decrease the number of available items in the storage.
			synchronized (item) {
				item.setCount(item.getCount() - 1);
				item.setReserved(item.getReserved() + 1);
			}
			// Add to txn history
			ItemHistory history = new ItemHistory(itemType,
					ItemHistory.Action.RESERVED, item, key);
			addTxnHistory(id, history);
			Trace.warn("RM::rmReserve(" + reserveType + ", " + id + ", "
					+ Integer.toString(flightNumber) + ", " + location
					+ ") OK.");
			return true;
		}
	}

	/**
	 * 
	 * @param id
	 *            id
	 * @param key
	 *            reserved item's key
	 * @param reservationCount
	 *            number of reservations for this item
	 * @return true if successful, else false
	 * @throws DeadlockException
	 */
	// Removes a reservation
	public boolean rmUnreserve(int id, String key, int reservationCount)
			throws DeadlockException {
		lm.Lock(id, key, LockManager.WRITE);
		ReservableItem item = (ReservableItem) readData(id, key);
		if (item == null) {
			Trace.info("RM:: Cannot unreserve item, it does not exist: " + key);
			return false;
		}
		item.setReserved(item.getReserved() - reservationCount);
		item.setCount(item.getCount() + reservationCount);
		Trace.info("RM:: item unreserved. Reserved count is now "
				+ item.getReserved() + ", available count is now "
				+ item.getCount());
		return true;
	}

	private void addTxnHistory(int txnId, ItemHistory item) {
		Vector<ItemHistory> v = txnHistory.get(txnId);
		if (v == null) {
			v = new Vector<ItemHistory>();
		}
		v.add(item);
		txnHistory.put(txnId, v);
		Trace.info("RM:: added " + item.getReservedItemKey() + " to txnHistory");
	}

	// Add flight reservation to this customer.
	@Override
	public boolean reserveFlight(int id, int customerId, int flightNumber) {
		return false;
	}

	// Add car reservation to this customer.
	@Override
	public boolean reserveCar(int id, int customerId, String location) {
		return false;
	}

	// Add room reservation to this customer.
	@Override
	public boolean reserveRoom(int id, int customerId, String location) {
		return false;
	}

	// Reserve an itinerary.
	@Override
	public boolean reserveItinerary(int id, int customerId,
			Vector flightNumbers, String location, boolean car, boolean room) {
		return false;
	}

	/* Start a new transaction and return its id. */
	@Override
	synchronized public int start() {
		return lastTrxnID.incrementAndGet();
	}

	/* Attempt to commit the given transaction; return true upon success. */
	@Override
	public boolean commit(int transactionId) {
		Trace.info("RM:: commiting transaction "+transactionId);
		// sanity check
		if(txnHistory.get(transactionId) == null) return false;
		txnHistory.remove(transactionId);
		
		return this.unlock(transactionId);
	}

	/* Abort the given transaction */
	@Override
	public boolean abort(int transactionId) {
		Trace.info("RM:: received abort request");
		Vector<ItemHistory> history = txnHistory.get(transactionId);
		if (history != null) {
			Trace.info("RM:: Reverting changes...");
			for (ItemHistory entry : history) {
				switch (entry.getAction()) {
				case ADDED:
					Trace.info("RM:: Remove added item " + entry.getReservedItemKey());
					removeData(transactionId, entry.getReservedItemKey());
					break;
				case DELETED:
					Trace.info("RM:: Adding back deleted item " + entry.getReservedItemKey());
					writeData(transactionId, entry.getReservedItemKey(), entry.getItem());
					break;
				case RESERVED:
					ReservableItem rmItem = (ReservableItem) readData(transactionId, entry.getReservedItemKey());
					Trace.info("RM:: Unreserving item " + rmItem.getKey());
					synchronized(rmItem) {
						rmItem.setCount(rmItem.getCount() + 1);
						rmItem.setReserved(rmItem.getReserved() - 1);
		        	}
					break;
				case UPDATED:
					ReservableItem oldItem = (ReservableItem) entry.getItem();
					Trace.info("RM:: Reverse updating item " + oldItem.getKey());
					oldItem.setCount(entry.getOldCount());
					oldItem.setPrice(entry.getOldPrice());
					oldItem.setReserved(entry.getOldReserved());
					
					writeData(transactionId, entry.getReservedItemKey(), oldItem);
					break;
				default:
					throw new IllegalStateException("A new action is detected, there is no implementation ready for this state");
				}
				
			}
		}
		txnHistory.remove(transactionId);
		return true;
	}

	/* Shut down gracefully */
	@Override
	public boolean shutdown() {
		Set<Integer> transactionsIds = new HashSet<>(txnHistory.keySet()); //prevent concurrent modification of map
		return transactionsIds.stream()
										.map(txn -> abort(txn))
										.reduce(true, (x,y)-> x&&y);
	}

	@Override
	public void removeTxn(int txnID) {
		txnHistory.remove(txnID);
	}

	@Override
	public boolean unlock(int txnID) {
		Trace.info("RM:: unlocking all locks of txn " + txnID);
		return lm.UnlockAll(txnID);
	}

	@Override
	public RMItem readFromStorage(int id, String key) {
		return readData(id, key);
	}

	@Override
	public void writeToStorage(int id, String key, RMItem value) {
		writeData(id, key, value);
	}

	@Override
	public RMItem deleteFromStorage(int id, String key) {
		return removeData(id, key);
	}
}
