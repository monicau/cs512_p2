package server;

//-------------------------------
//Adapted from  
//CSE 593
//-------------------------------

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.MalformedURLException;
import java.nio.channels.AsynchronousServerSocketChannel;

import javax.jws.WebMethod;
import javax.jws.WebService;

import lockmanager.DeadlockException;
import lockmanager.LockManager;
import TransactionManager.InvalidTransactionException;
import TransactionManager.TransactionManager;
import TransactionManager.TransactionManager.RM;

import com.google.gson.Gson;


@WebService(endpointInterface = "server.ws.ResourceManager")
public class MiddlewareImpl implements server.ws.ResourceManager {
	ResourceManager proxyFlight;
	ResourceManager proxyCar;
	ResourceManager proxyRoom;
	ResourceManagerImplService service;
	boolean useWebService;
	private LockManager lm;
	private TransactionManager tm;
	private Shadower shadower;
	private RMMap<Integer, Vector<ItemHistory>> txnHistory;

	int next_port = 8098;
	Map<Integer, Socket> resourceManagers = new HashMap<>();
	Map<Integer, OutputStream> rmOS = new ConcurrentHashMap<>();
	Map<Integer, InputStream> rmIS = new ConcurrentHashMap<>();

	List<Integer> rmPorts = new CopyOnWriteArrayList<>();
	List<InetAddress> rmAddresses = new CopyOnWriteArrayList<>();
	private TCPServiceRequest tcp;
	protected RMMap m_itemHT = new RMMap<>();

	
	public MiddlewareImpl(){
		System.out.println("Starting middleware");
		lm = new LockManager();
		txnHistory = new RMMap<Integer, Vector<ItemHistory>>();
		shadower = new Shadower("mw"); 
		RMMap recovery = shadower.recover();
		if (recovery != null ) {
			System.out.println("MW:: Recovered data");
			m_itemHT = recovery;
		}
		//Determine if we are using web services or tcp
		try {
			BufferedReader reader = new BufferedReader(new FileReader(new File("serviceType.txt")));
			try {
				String line = reader.readLine();
				if (line.equals("ws")) {
					useWebService = true;
				} else {
					useWebService = false;
				}
				next_port = Integer.parseInt(reader.readLine());
			} catch (IOException e) {
				Trace.info("ERROR: IOException, cannot read serviceType.txt");
			}
		} catch (FileNotFoundException e) {
			Trace.info("ERROR: Cannot find serviceType.txt");
		}
		if (useWebService) {
			//Create proxies
			String flightServiceHost = null;
			Integer flightServicePort = null;
			String carServiceHost = null;
			Integer carServicePort = null;
			String roomServiceHost = null;
			Integer roomServicePort = null;
			try {
				BufferedReader reader = new BufferedReader(new FileReader(new File("rm.txt")));
				String[] line = new String[6];
				try {
					for (int i=0; i<6; i++) {
						line[i] = reader.readLine();
						Trace.info("Read: " + line[i]);
					}
					flightServiceHost = line[0];
					flightServicePort = Integer.parseInt(line[1]);
					carServiceHost = line[2];
					carServicePort = Integer.parseInt(line[3]);
					roomServiceHost = line[4];
					roomServicePort = Integer.parseInt(line[5]);
					try {
						URL wsdlLocation = new URL("http", flightServiceHost, flightServicePort, "/" + "rm" + "/rm?wsdl");
						service = new ResourceManagerImplService(wsdlLocation);
						proxyFlight = service.getResourceManagerImplPort();

						wsdlLocation = new URL("http", carServiceHost, carServicePort, "/" + "rm" + "/rm?wsdl");
						service = new ResourceManagerImplService(wsdlLocation);
						proxyCar= service.getResourceManagerImplPort();

						wsdlLocation = new URL("http", roomServiceHost, roomServicePort, "/" + "rm" + "/rm?wsdl");
						service = new ResourceManagerImplService(wsdlLocation);
						proxyRoom= service.getResourceManagerImplPort();
					} catch (MalformedURLException e) {
						Trace.info("ERROR!! Malformed url.");
					}
				} catch (IOException e) {
					Trace.info("ERROR: Reading line failed! IOException.");
				}
			} catch (FileNotFoundException e) {
				Trace.info("ERROR: File not found!");
			}
			tm = new TransactionManager(this, proxyFlight, proxyCar, proxyRoom, 60000);
		} 
		else {
			// sockets
			try{
				Messenger messenger = new Messenger(9090);
				messenger.onMessage = (message, socket, outputstream) -> {
					System.out.println("Received a message");
					try{
						if(message.equals("[port?]")){
							InetAddress inetAddress = socket.getInetAddress();
							int foreignPort = socket.getPort();
							System.out.println("Received port request");
							int port = next_port++;

							rmAddresses.add(inetAddress);
							rmPorts.add(port);

							PrintWriter writer = new PrintWriter(outputstream, true);
							System.out.println("Giving out port "+port+" to "+inetAddress+":"+foreignPort);
							writer.println(port+"");
							
							if(rmPorts.size() == 4){
								System.out.println("Constructed the TCP service");
								
								for (int i = 0; i < rmPorts.size(); i++) {
									Integer p = rmPorts.get(i);
									Socket s; 
									try{
										s = new Socket(rmAddresses.get(i), p);
									}
									catch(Exception e){
										Thread.sleep(50);
										s = new Socket(rmAddresses.get(i),p);
									}
									resourceManagers.put(p, s);
									rmOS.put(p, s.getOutputStream());
									rmIS.put(p, s.getInputStream());
								}
								tcp = new TCPServiceRequest(messenger, rmOS.get(rmPorts.get(0)), messenger, rmOS.get(rmPorts.get(1)), messenger, rmOS.get(rmPorts.get(2)), messenger, rmOS.get(rmPorts.get(3)));
								
							}
						}
						else if(message.indexOf(':') != -1){
							// This is dealt with else where
							System.out.println(message);
							Thread.sleep(1000);
						}
						else{		// this is a "api" call
							// Messages have form  method_name(type1,type2,...,typen)var1,var2,...,varn
							System.out.println("Parsing message "+message);
							int split1 = message.indexOf('(');
							int split2 = message.indexOf(')');
							String methodname = message.substring(0, split1);
							String methodname_lower = methodname.toLowerCase();
							System.out.println("Parsed message: "+methodname);
							// if this is the intended destination
							if(methodname_lower.contains("itinerary") || methodname_lower.contains("customer")){
								// find method
								System.out.println("Resolving request "+methodname+" locally in middleware");
								String paramtypes = message.substring(split1+1, split2);
								String varsvalues = message.substring(split2+1);
								String[] splittedParams = paramtypes.split(",");
								Class<?>[] types = new Class<?>[splittedParams.length];
								for (int i = 0; i < splittedParams.length; i++) {
									String param = splittedParams[i];
									try {
										types[i] = param.equals("int")? int.class:
													param.equals("boolean")? boolean.class:
													param.equals("String")? String.class:
													Class.forName(param);
									} catch (Exception e) {
										e.printStackTrace();
									}
								}
								Method method = this.getClass().getMethod(methodname, types);
								varsvalues = "["+varsvalues+"]";
								Object[] vars = new Gson().fromJson(varsvalues, Object[].class);
								for (int i = 0; i < vars.length; i++) {
									try{
										vars[i] = types[i].cast(vars[i]);
									}
									catch(Exception e){
										try{
											vars[i] = (int)(((Double) vars[i]).doubleValue());
										}
										catch(Exception e1){
											try{
												vars[i] = (boolean)(((Boolean) vars[i]).booleanValue());
											}
											catch(Exception e2){
												try{
													vars[i] = (String) vars[i];
												}
												catch(Exception e3){
													vars[i] = new Vector<>((List)vars[i]);
												}
											}
										}
									}
								}
								Object result = method.invoke(this, vars);

								PrintWriter writer = new PrintWriter(outputstream,true);
								System.out.println("Returning result "+result+" to "+socket.getPort());
								writer.println(methodname+":"+result);
							}
							else{
								// forward it to rm
								System.out.println("Forwarding request "+methodname+" to RM");
								Integer port = -1;
								
								if(methodname_lower.contains("flight")){
									port = rmPorts.get(0);
									System.out.println("Preparing for flight");
									messenger.eventHandlers.put( name -> name.contains(methodname),
											msg -> { // relay back to client
												PrintWriter writer = new PrintWriter(outputstream, true);
												System.out.println("Relaying "+msg+" to "+socket.getPort());
												writer.println(msg);
											});
								}
								else if (methodname_lower.contains("car")){
									port = rmPorts.get(1);
									System.out.println("Preparing for car");
									messenger.eventHandlers.put( name -> name.contains(methodname),
											msg -> { // relay back to client
												PrintWriter writer = new PrintWriter(outputstream, true);
												System.out.println("Relaying "+msg+" to "+socket.getPort());
												writer.println(msg);
											});
								}
								else if (methodname_lower.contains("room")){
									port = rmPorts.get(2);
									System.out.println("Preparing for room");
									messenger.eventHandlers.put( name -> name.contains(methodname),
											msg -> { // relay back to client
												PrintWriter writer = new PrintWriter(outputstream, true);
												System.out.println("Relaying "+msg+" to "+socket.getPort());
												writer.println(msg);
											});
								}
								else{
									System.out.println("The method "+methodname+" is not recognized");
								}
								System.out.println(messenger.eventHandlers);
								
								System.out.println(">>>Event handler ready");
								
								OutputStream os = rmOS.get(port);
								System.out.println("OUTPUT STREAM: " + os);
								PrintWriter writer = new PrintWriter(os, true);
								writer.println(message);
								System.out.println("Sent request "+methodname+" to rm at port "+port);
							}
						}
					}
					catch(Exception e){
						e.printStackTrace();
					}
				};
				messenger.start();
			}
			catch(IOException exception){
				throw new IllegalStateException("The port 9090 is in use. Please kill that process.");
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	// Basic operations on RMItem //

	// Read a data item.
	private RMItem readData(int id, String key) {
		synchronized(m_itemHT) {
			return (RMItem) m_itemHT.get(key);
		}
	}

	// Write a data item.
	private void writeData(int id, String key, RMItem value) {
		synchronized(m_itemHT) {
			m_itemHT.put(key, value);
		}
	}

	// Remove the item out of storage.
	protected RMItem removeData(int id, String key) {
		synchronized(m_itemHT) {
			return (RMItem) m_itemHT.remove(key);
		}
	}


	// Basic operations on ReservableItem //

	// Delete the entire item. Method for resource manager only.
	protected boolean deleteItem(int id, String key) {
		return false;
	}

	// Query the number of available seats/rooms/cars.
	protected int queryNum(int id, String key) {
		Trace.info("MW::queryNum(" + id + ", " + key + ") called.");
		ReservableItem curObj = (ReservableItem) readData(id, key);
		int value = 0;  
		if (curObj != null) {
			value = curObj.getCount();
		}
		Trace.info("MW::queryNum(" + id + ", " + key + ") OK: " + value);
		return value;
	}    

	// Query the price of an item.
	protected int queryPrice(int id, String key) {
		Trace.info("MW::queryCarsPrice(" + id + ", " + key + ") called.");
		ReservableItem curObj = (ReservableItem) readData(id, key);
		int value = 0; 
		if (curObj != null) {
			value = curObj.getPrice();
		}
		Trace.info("MW::queryCarsPrice(" + id + ", " + key + ") OK: $" + value);
		return value;
	}

	//Public version
	public int getPrice(int id, String key) throws InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		return queryPrice(id, key);
	}


	// Flight operations //

	// Create a new flight, or add seats to existing flight.
	// Note: if flightPrice <= 0 and the flight already exists, it maintains 
	// its current price.
	@Override
	public boolean addFlight(int id, int flightNumber, int numSeats, int flightPrice) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		Trace.info("MW::addFlight(" + id + ", " + flightNumber 
				+ ", $" + flightPrice + ", " + numSeats + ") called.");
		tm.ping(id);
		tm.enlist(id, RM.FLIGHT);
		boolean r = true;
		try {
			r = proxyFlight.addFlight(id, flightNumber, numSeats, flightPrice);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
		Trace.info("MW:: addFlight succeeded:" + Boolean.toString(r));
		return r;
	}

	@Override
	public boolean deleteFlight(int id, int flightNumber) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.FLIGHT);
		try {
			return proxyFlight.deleteFlight(id, flightNumber);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}

	// Returns the number of empty seats on this flight.
	@Override
	public int queryFlight(int id, int flightNumber) throws DeadlockException, InvalidTransactionException{
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.FLIGHT);
		try {
			return proxyFlight.queryFlight(id, flightNumber);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}

	// Returns price of this flight.
	public int queryFlightPrice(int id, int flightNumber) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.FLIGHT);
		try {
			return proxyFlight.queryFlightPrice(id, flightNumber);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}

	/*
 // Returns the number of reservations for this flight. 
 public int queryFlightReservations(int id, int flightNumber) {
     Trace.info("MW::queryFlightReservations(" + id 
             + ", #" + flightNumber + ") called.");
     RMInteger numReservations = (RMInteger) readData(id, 
             Flight.getNumReservationsKey(flightNumber));
     if (numReservations == null) {
         numReservations = new RMInteger(0);
    }
     Trace.info("MW::queryFlightReservations(" + id + 
             ", #" + flightNumber + ") = " + numReservations);
     return numReservations.getValue();
 }
	 */

	/*
 // Frees flight reservation record. Flight reservation records help us 
 // make sure we don't delete a flight if one or more customers are 
 // holding reservations.
 public boolean freeFlightReservation(int id, int flightNumber) {
     Trace.info("MW::freeFlightReservations(" + id + ", " 
             + flightNumber + ") called.");
     RMInteger numReservations = (RMInteger) readData(id, 
             Flight.getNumReservationsKey(flightNumber));
     if (numReservations != null) {
         numReservations = new RMInteger(
                 Math.max(0, numReservations.getValue() - 1));
     }
     writeData(id, Flight.getNumReservationsKey(flightNumber), numReservations);
     Trace.info("MW::freeFlightReservations(" + id + ", " 
             + flightNumber + ") OK: reservations = " + numReservations);
     return true;
 }
	 */


	// Car operations //

	// Create a new car location or add cars to an existing location.
	// Note: if price <= 0 and the car location already exists, it maintains 
	// its current price.
	@Override
	public boolean addCars(int id, String location, int numCars, int carPrice) throws DeadlockException, InvalidTransactionException{
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		Trace.info("MW::addCars(" + id + ", " + location + ", " 
				+ numCars + ", $" + carPrice + ") called.");
		tm.ping(id);
		tm.enlist(id, RM.CAR);
		boolean r = true;
		try {
			r = proxyCar.addCars(id, location, numCars, carPrice);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
		Trace.info("MW::addCar succeeded: " + Boolean.toString(r));
		return r;
	}

	// Delete cars from a location.
	@Override
	public boolean deleteCars(int id, String location) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.CAR);
		try {
			return proxyCar.deleteCars(id, location);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}

	// Returns the number of cars available at a location.
	@Override
	public int queryCars(int id, String location) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.CAR);
		try {
			return proxyCar.queryCars(id, location);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}

	// Returns price of cars at this location.
	@Override
	public int queryCarsPrice(int id, String location) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.CAR);
		try {
			return proxyCar.queryCarsPrice(id, location);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}


	// Room operations //

	// Create a new room location or add rooms to an existing location.
	// Note: if price <= 0 and the room location already exists, it maintains 
	// its current price.
	@Override
	public boolean addRooms(int id, String location, int numRooms, int roomPrice) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		Trace.info("MW::addRooms(" + id + ", " + location + ", " 
				+ numRooms + ", $" + roomPrice + ") called.");
		tm.ping(id);
		tm.enlist(id, RM.ROOM);
		boolean r = true;
		try {
			r = proxyRoom.addRooms(id, location, numRooms, roomPrice);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
		Trace.info("MW::addRooms succeeded: " + Boolean.toString(r));
		return r;
	}

	// Delete rooms from a location.
	@Override
	public boolean deleteRooms(int id, String location) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.ROOM);
		try {
			return proxyRoom.deleteRooms(id, location);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}

	// Returns the number of rooms available at a location.
	@Override
	public int queryRooms(int id, String location) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.ROOM);
		try {
			return proxyRoom.queryRooms(id, location);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}

	// Returns room price at this location.
	@Override
	public int queryRoomsPrice(int id, String location) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		tm.enlist(id, RM.ROOM);
		try {
			return proxyRoom.queryRoomsPrice(id, location);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
	}


	// Customer operations //

	@Override
	public int newCustomer(int id) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		Trace.info("INFO: MW::newCustomer(" + id + ") called.");
		tm.ping(id);
		tm.enlist(id, RM.CUSTOMER);
		// Generate a globally unique Id for the new customer.
		int customerId;
		synchronized(this) {
			customerId = Integer.parseInt(String.valueOf(id) +
					String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
					String.valueOf(Math.round(Math.random() * 100 + 1)));
		}
		//Lock on new customer
		lm.Lock(id, "customer_" + customerId, LockManager.WRITE);
		Customer cust = new Customer(customerId);
		
		//Add customer to txn history
		ItemHistory backup = new ItemHistory(ItemHistory.ItemType.CUSTOMER, ItemHistory.Action.ADDED, cust, null);
		addCustomerHistory(id, backup);
		
		writeData(id, cust.getKey(), cust);
		Trace.info("MW::newCustomer(" + id + ") OK: " + customerId);
		return customerId;
	}

	// This method makes testing easier.
	@Override
	public boolean newCustomerId(int id, int customerId) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		Trace.info("INFO: MW::newCustomer(" + id + ", " + customerId + ") called.");
		tm.enlist(id, RM.CUSTOMER);
		//Lock on new customer
		lm.Lock(id, "customer_" + customerId, LockManager.WRITE);
		Customer cust = (Customer) readData(id, Customer.getKey(customerId));
		if (cust == null) {
			//Create customer and save to storage
			cust = new Customer(customerId);
			writeData(id, cust.getKey(), cust);
			//Add customer to txn history
			ItemHistory backup = new ItemHistory(ItemHistory.ItemType.CUSTOMER, ItemHistory.Action.ADDED, cust, null);
			addCustomerHistory(id, backup);
			Trace.info("INFO: MW::newCustomer(" + id + ", " + customerId + ") OK.");
			return true;
		} else {
			Trace.info("INFO: MW::newCustomer(" + id + ", " + 
					customerId + ") failed: customer already exists.");
			return false;
		}
	}

	// Delete customer from the database. 
	@Override
	public boolean deleteCustomer(int id, int customerId) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		Trace.info("MW::deleteCustomer(" + id + ", " + customerId + ") called.");
		tm.ping(id);
		tm.enlist(id, RM.CUSTOMER);
		//Write lock on customer
		lm.Lock(id, "customer_" + customerId, LockManager.WRITE);
		Customer cust = (Customer) readData(id, Customer.getKey(customerId));
		if (cust == null) {
			Trace.warn("MW::deleteCustomer(" + id + ", " 
					+ customerId + ") failed: customer doesn't exist.");
			return false;
		} else {            
			// Increase the reserved numbers of all reservable items that 
			// the customer reserved. 
			RMMap reservationHT = cust.getReservations();
			for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
				String reservedKey = (String) (e.nextElement());
				ReservedItem reservedItem = cust.getReservedItem(reservedKey);
				Trace.info("MW::deleteCustomer(" + id + ", " + customerId + "): " 
						+ "deleting " + reservedItem.getCount() + " reservations "
						+ "for item " + reservedItem.getKey());
				//TODO: proxy dis shit
				//Since we don't know what type of item it is, we will try one proxy at a time
				Trace.info("MW::Attempting to unreserve " + reservedItem.getKey());
				try {
					if (proxyFlight.rmUnreserve(id, reservedItem.getKey(), reservedItem.getCount()) == false) {
						Trace.info("MW::unreserving flight failed. Trying car..");
						if (proxyCar.rmUnreserve(id, reservedItem.getKey(), reservedItem.getCount()) == false) {
							Trace.info("MW::unreserving car failed. Trying room..");
							if (proxyRoom.rmUnreserve(id, reservedItem.getKey(), reservedItem.getCount()) == false) {
								Trace.info("MW::fail to cancel reservation for room too.");
							}
						}
					}
				} catch (Exception ex) {
					throw new DeadlockException(id, ex.getMessage());
				}
			}
			// Add action to txn History
			ItemHistory backup = new ItemHistory(ItemHistory.ItemType.CUSTOMER, ItemHistory.Action.DELETED, cust, null);
			addCustomerHistory(id, backup);
			
			// Remove the customer from the storage.
			removeData(id, cust.getKey());
			Trace.info("MW::deleteCustomer(" + id + ", " + customerId + ") OK.");
			return true;
		}
	}

	// Return data structure containing customer reservation info. 
	// Returns null if the customer doesn't exist. 
	// Returns empty RMMap if customer exists but has no reservations.
	public RMMap getCustomerReservations(int id, int customerId) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		Trace.info("MW::getCustomerReservations(" + id + ", " 
				+ customerId + ") called.");
		tm.ping(id);
		tm.enlist(id, RM.CUSTOMER);
		// Read lock on customer
		lm.Lock(id, "customer_" + customerId, LockManager.READ);
		Customer cust = (Customer) readData(id, Customer.getKey(customerId));
		if (cust == null) {
			Trace.info("MW::getCustomerReservations(" + id + ", " 
					+ customerId + ") failed: customer doesn't exist.");
			return null;
		} else {
			return cust.getReservations();
		}
	}

	// Return a bill.
	@Override
	public String queryCustomerInfo(int id, int customerId) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		Trace.info("MW::queryCustomerInfo(" + id + ", " + customerId + ") called.");
		tm.ping(id);
		tm.enlist(id, RM.CUSTOMER);
		// Read lock on customer
		lm.Lock(id, "customer_" + customerId, LockManager.READ);
		Customer cust = (Customer) readData(id, Customer.getKey(customerId));
		if (cust == null) {
			Trace.warn("MW::queryCustomerInfo(" + id + ", " 
					+ customerId + ") failed: customer doesn't exist.");
			// Returning an empty bill means that the customer doesn't exist.
			return "";
		} else {
			String s = cust.printBill();
			Trace.info("MW::queryCustomerInfo(" + id + ", " + customerId + "): \n");
			System.out.println(s);
			return s;
		}
	}

	//Method for resource manager only.
	public boolean reserveItem(String reserveType, int id, int flightNumber, String location) throws DeadlockException {
		System.out.println("MW PROXY GOT THRU YEY");
		return false;
	}
	//Method for resource manager only.
	public boolean rmUnreserve(int id, String key, int reservationCount) throws DeadlockException{
		return false;
	}
	// Add flight reservation to this customer.  
	@Override
	public boolean reserveFlight(int id, int customerId, int flightNumber) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		// Read customer object if it exists (and read lock it).
		lm.Lock(id, "customer_" + customerId, LockManager.WRITE);
		tm.enlist(id, RM.CUSTOMER);
		Customer cust = (Customer) readData(id, Customer.getKey(customerId));
		if (cust == null) {
			Trace.warn("MW::reserveFlight(" + id + ", " + customerId +  ", " + flightNumber + ") failed: customer doesn't exist.");
			return false;
		} 
		//Reserve!

		//Save reservation info to resource manager
		boolean result;
		try {
			tm.enlist(id, RM.FLIGHT);
			result = proxyFlight.reserveItem("flight", id, flightNumber, null);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
		if (result == true) {
			//Create a backup of customer and reservation before modifying it
			ItemHistory backupCustomer = new ItemHistory(ItemHistory.ItemType.CUSTOMER, ItemHistory.Action.RESERVED, cust, "flight-"+flightNumber);
			addCustomerHistory(id, backupCustomer);
			//Save reservation info to customer object
			try {
				cust.reserve(Flight.getKey(flightNumber), String.valueOf(flightNumber), proxyFlight.getPrice(id, Flight.getKey(flightNumber)));
			} catch (Exception e) {
				if (e.getMessage().contains("deadlock")) {
					throw new DeadlockException(id, e.getMessage());
				} else {
					throw new InvalidTransactionException(e.getMessage());
				}
			}
			writeData(id, cust.getKey(), cust);
		}
		Trace.warn("MW::reserveFlight succeeded: " + result);
		return result;
	}

	// Add car reservation to this customer. 
	@Override
	public boolean reserveCar(int id, int customerId, String location) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		// Read customer object if it exists (and read lock it).
		lm.Lock(id, "customer_" + customerId, LockManager.WRITE);
		tm.enlist(id, RM.CUSTOMER);
		Customer cust = (Customer) readData(id, Customer.getKey(customerId));
		if (cust == null) {
			Trace.warn("MW::reserveCar(" + id + ", " + customerId +  ", " + location + ") failed: customer doesn't exist.");
			return false;
		} 
		//Reserve!

		//Save reservation info to resource manager
		boolean result;
		try {
			tm.enlist(id, RM.CAR);
			result = proxyCar.reserveItem("car", id, -1, location);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
		if (result == true) {
			//Create a backup of customer and reservation before modifying it
			ItemHistory backupCustomer = new ItemHistory(ItemHistory.ItemType.CUSTOMER, ItemHistory.Action.RESERVED, cust, Car.getKey(location));
			addCustomerHistory(id, backupCustomer);
			//Save reservation info to customer object
			try {
				cust.reserve(Car.getKey(location), location, proxyCar.getPrice(id, Car.getKey(location)));
			} catch (Exception e) {
				if (e.getMessage().contains("deadlock")) {
					throw new DeadlockException(id, e.getMessage());
				} else {
					throw new InvalidTransactionException(e.getMessage());
				}
			}
			writeData(id, cust.getKey(), cust);
		}
		Trace.warn("MW::reserveCar succeeded: " + result);
		return result;
	}

	// Add room reservation to this customer. 
	@Override
	public boolean reserveRoom(int id, int customerId, String location) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		// Read customer object if it exists (and read lock it).
		lm.Lock(id, "customer_" + customerId, LockManager.WRITE);
		tm.enlist(id, RM.CUSTOMER);
		Customer cust = (Customer) readData(id, Customer.getKey(customerId));
		if (cust == null) {
			Trace.warn("MW::reserveRoom(" + id + ", " + customerId +  ", " + location + ") failed: customer doesn't exist.");
			return false;
		} 
		//Reserve!

		//Save reservation info to resource manager
		boolean result;
		try {
			tm.enlist(id, RM.ROOM);
			result = proxyRoom.reserveItem("room", id, -1, location);
		} catch (Exception e) {
			if (e.getMessage().contains("deadlock")) {
				throw new DeadlockException(id, e.getMessage());
			} else {
				throw new InvalidTransactionException(e.getMessage());
			}
		}
		if (result == true) {
			//Create a backup of customer and reservation before modifying it
			ItemHistory backupCustomer = new ItemHistory(ItemHistory.ItemType.CUSTOMER, ItemHistory.Action.RESERVED, cust, Room.getKey(location));
			addCustomerHistory(id, backupCustomer);
			//Save reservation info to customer object
			try {
				cust.reserve(Room.getKey(location), location, proxyRoom.getPrice(id, Room.getKey(location)));
			} catch (Exception e) {
				if (e.getMessage().contains("deadlock")) {
					throw new DeadlockException(id, e.getMessage());
				} else {
					throw new InvalidTransactionException(e.getMessage());
				}
			}
			writeData(id, cust.getKey(), cust);
		}
		Trace.warn("MW::reserveRoom succeeded: " + result);
		return result;
	}


	// Reserve an itinerary.
	@Override
	public boolean reserveItinerary(int id, int customerId, Vector flightNumbers, String location, boolean car, boolean room) throws DeadlockException, InvalidTransactionException {
		if (!tm.isValidTransaction(id)) throw new InvalidTransactionException();
		tm.ping(id);
		Trace.info("MW::reserve itinerary");
		// Enlist resource managers
		if (flightNumbers.size()>0) tm.enlist(id, RM.FLIGHT);
		if (car) tm.enlist(id, RM.CAR);
		if (room) tm.enlist(id, RM.ROOM);
		
		boolean locksAcquired = true;
		locksAcquired &= lm.Lock(id, Room.getKey(location), LockManager.WRITE);
		locksAcquired &= lm.Lock(id, Car.getKey(location), LockManager.WRITE);
		Vector<Integer> flights = flightNumbers;
		locksAcquired &= flights.stream()
								.map(number -> {
									try{
										return lm.Lock(id, Flight.getKey(number), LockManager.WRITE);
									}
									catch(Exception e){
										return false;
									}
								})
								.reduce(true, (x,y)-> x&&y);
		
		// Check vacancy before trying to reserve
		for (Object element: flightNumbers) {
			int flightNumber;
			if(useWebService){
				String flightNumberString= (String) element;
				flightNumber = Integer.parseInt(flightNumberString);
			}
			else{
				flightNumber = (int)((Double) element).doubleValue();
			}
			
			if (queryFlight(id,flightNumber)<1) {
				Trace.info("MW::No free seats on flight " + flightNumber);
				return false;
			}
		}
		
		if (car && queryCars(id, location)<1) {
			Trace.info("MW::No free cars at " + location + " to rent.");
			return false;
		}
		if (room && queryRooms(id, location)<1) {
			Trace.info("MW::No free rooms at " + location + " to rent.");
			return false;
		}
		//Now try to reserve all the items
		if (car) {
			Trace.info("MW::Reserving car at " + location);
			boolean reserveCarResult = false;
			if(useWebService) {
				reserveCarResult = reserveCar(id, customerId, location);
			}
			//return false now if reserving car failed
			if (reserveCarResult == false) {
				//Unreserve car at customer object
				rollback(id, customerId, flightNumbers, location, car, false);
				return false;
			}
		}
		if (room) {
			Trace.info("MW::Reserving room at " + location);
			boolean reserveRoomResult = reserveRoom(id, customerId, location);
			if (reserveRoomResult == false && car) {
				//Cancel car reservation
				try {
					proxyCar.rmUnreserve(id, Car.getKey(location), 1);
				} catch (Exception e) {
					throw new DeadlockException(id, e.getMessage());
				}
				//Unreserve both car and room from customer
				rollback(id, customerId, flightNumbers, location, car, room);
				return false;
			}
		}
		boolean[] reserveFlightResult = new boolean[flightNumbers.size()];
		for (boolean flight : reserveFlightResult) {
			flight = false;
		}
		boolean reserveFlightSuccess = true;
		for (int i=0; i<flightNumbers.size(); i++) {
			String flightNumberString= (String) flightNumbers.get(i);
			int flightNumber = Integer.parseInt(flightNumberString);
			Trace.info("MW::Reserving flight: " + flightNumber);
			reserveFlightResult[i] = reserveFlight(id, customerId, flightNumber);
			//Break out of reserving flights if this flight reservation failed
			if (reserveFlightResult[i]==false) {
				reserveFlightSuccess = false;
				break;
			}
		}
		if (reserveFlightSuccess == false) {
			//Roll back any successful car, room and flight reservations 
			if (car) {
				try {
					proxyCar.rmUnreserve(id, Car.getKey(location), 1);
				} catch (Exception e) {
					if (e.getMessage().contains("deadlock")) {
						throw new DeadlockException(id, e.getMessage());
					} else {
						throw new InvalidTransactionException(e.getMessage());
					}
				}
			}
			if (room) {
				try {
					proxyRoom.rmUnreserve(id, Room.getKey(location), 1);
				} catch (Exception e) {
					if (e.getMessage().contains("deadlock")) {
						throw new DeadlockException(id, e.getMessage());
					} else {
						throw new InvalidTransactionException(e.getMessage());
					}
				}
			}
			for (int i=0; i<reserveFlightResult.length; i++) {
				if (reserveFlightResult[i]==true) {
					int flightNum = Integer.parseInt((String) flightNumbers.get(i));
					try {
						proxyFlight.rmUnreserve(id, Flight.getKey(flightNum), 1);
					} catch (Exception e) {
						if (e.getMessage().contains("deadlock")) {
							throw new DeadlockException(id, e.getMessage());
						} else {
							throw new InvalidTransactionException(e.getMessage());
						}
					}
				}
			}
			rollback(id, customerId, flightNumbers, location, car, room);
			return false;
		}
		return true;
	}
	
    /* Start a new transaction and return its id. */
	@Override
	public int start() {
		return tm.start();
	}
    /* Attempt to commit the given transaction; return true upon success. */
	@Override
	public boolean commit(int transactionId) throws InvalidTransactionException {
		if (!tm.isValidTransaction(transactionId)) throw new InvalidTransactionException();
		return tm.commit(transactionId);
	}
	/* Local commit */
	public boolean localCommit(int transactionId) {
		removeTxn(transactionId);
		boolean r = unlock(transactionId);
		shadower.commitToStorage(m_itemHT);
		return r;
	}
    /* Abort the given transaction */
	@Override
	public boolean abort(int transactionId) throws InvalidTransactionException {
		if (!tm.isValidTransaction(transactionId)) throw new InvalidTransactionException();
		return tm.abort(transactionId);
	}
    /* Shut down gracefully */
	@Override
	public boolean shutdown() {
		//TODO persistent data handling
		tm.stopHeartbeatSweeper();
		Set<Integer> transactionsIds = new HashSet<>(txnHistory.keySet()); //prevent concurrent modification of map
		boolean r = transactionsIds.stream()
										.map(txn -> {
										try {	
											return abort(txn);
										} catch (Exception e) {
											return false;
										}
										})
										.reduce(true, (x,y)-> x&&y);
		proxyFlight.shutdown();
		proxyCar.shutdown();
		proxyRoom.shutdown();
		// Schedule a shutdown
		new TimedExit();
		return r;
	}
	
	public boolean abortCustomer(int transactionId) {
		System.out.println("MW:: Aborting a transaction " + transactionId);
		// Revert changes
		Vector<ItemHistory> history = txnHistory.get(transactionId);
		if (history != null) {
			System.out.println("MW:: Reverting changes...");
			for (ItemHistory item : history) {
				if (item.getAction()==ItemHistory.Action.ADDED) {
					// Delete item from storage
					System.out.println("MW:: Deleting added customer.");
					removeData(transactionId, ((Customer)item.getItem()).getKey());
				} else if (item.getAction()==ItemHistory.Action.DELETED) {
					// Add back to storage
					System.out.println("MW:: Adding a deleted customer.");
					writeData(transactionId, ((Customer)item.getItem()).getKey(), ((Customer)item.getItem()));
				} else {
					// Item was updated. Revert back to old version
					System.out.println("MW:: Reverting customer to its old stats");
					removeData(transactionId, ((Customer)item.getItem()).getKey());
					//Remove reservation from customer object
					Customer c = (Customer) item.getItem();
					String key = item.getReservedItemKey();
					System.out.println("MW::Abort is unreserving " + key);
					c.unreserve(key);
					//Save updated customer object to storage
					writeData(transactionId, c.getKey(), c);
				}
			}
		}
		txnHistory.remove(transactionId);
		return true;
	}
	
	private void addCustomerHistory(int txnId, ItemHistory item) {
		Vector<ItemHistory> v = txnHistory.get(txnId);
		if (v == null) {
			v = new Vector<ItemHistory>();
		} 
		v.add(item);
		txnHistory.put(txnId, v);
	}
	//Not sponsored by walmart. Rolls back customer's info on what it has reserved
	private void rollback(int txnId, int customerId, Vector flightNumbers, String location, boolean car, boolean room) {
		System.out.println("MW:: ROLLBACK!  Car:"+car + ", Room:" + room);
		Vector<ItemHistory> items = txnHistory.get(txnId);
		for (ItemHistory item : items) {
			//We know here in MW that all items are Customer objects
			Customer cust = ((Customer)item.getItem());
			if (cust.getId()==customerId) {
				//Delete customer object from storage
				removeData(txnId, cust.getKey());
				//Rollback!
				for (Object element : flightNumbers) {
					String flightNumberString= (String) element;
					int flightNumber = Integer.parseInt(flightNumberString);
					cust.unreserve("flight-"+flightNumberString);
				}
				//Rollback car and room if they were reserved
				if (car) {
					System.out.println("MW:: Unreserving car-"+location + " for customer " + cust.getKey());
					cust.unreserve("car-" + location);
				}
				if (room) {
					System.out.println("MW:: Unreserving room-"+location + " for customer " + cust.getKey());
					cust.unreserve("room-" + location);
				}
				//Save updated customer object to storage
				writeData(txnId, cust.getKey(), cust);
			}
		}
		
	}
	
	@Override
	public boolean unlock(int txnID) {   
		System.out.println("MW:: unlocking all locks of txn " + txnID);
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
	
	@Override
	public void removeTxn(int txnID) {
		txnHistory.remove(txnID);
	}

	@Override
	public void crash(String target) {
		System.out.println("MW:: crashing " + target);
		if (target.equals("mw")) {
			selfDestruct();
		} else if (target.equals("flight")) {
			proxyFlight.selfDestruct();
		} else if (target.equals("car")) {
			proxyCar.selfDestruct();
		} else if (target.equals("room")) {
			proxyRoom.selfDestruct();
		}
	}
	
	@Override
	public void selfDestruct() {
		tm.stopHeartbeatSweeper();
		// Schedule a shutdown
		new TimedExit();
	}
}

