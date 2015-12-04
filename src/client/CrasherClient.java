package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Random;

import com.sun.xml.ws.api.streaming.XMLStreamReaderFactory.Default;

public class CrasherClient extends WSClient {

	public CrasherClient(String serviceName, String serviceHost, int servicePort) throws MalformedURLException {
		super(serviceName, serviceHost, servicePort);
	}
	public static void main(String[] args) {
		try {
            if (args.length != 4) {
                System.out.println("Usage: MyClient <service-name> " 
                        + "<service-host> <service-port> <service-type>");
                System.exit(-1);
            }
	        String serviceName = args[0];
	        String serviceHost = args[1];
	        int servicePort = Integer.parseInt(args[2]);
	
	        CrasherClient client = new CrasherClient(serviceName, serviceHost, servicePort);
	
//	        client.run();
	        client.runNum();
        } catch(Exception e) {
            e.printStackTrace();
        }
	}
	
	private void runNum(){
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Which test do you want to run? (1-10)");
		String read = readIn(in);
		int crashCase = Integer.parseInt(read);
		String target = (crashCase <= 7) ? "mw" : getTarget(in);
		proxy.crashPoint(target, crashCase);
		System.out.println("Crash point has been set");
		System.out.println("Change default vote? Default votes are yesses.  (y/n)");
		handleVote(in);
		System.out.println("Starting automated transaction ");
		try {
			automatedTransaction(crashCase, target);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void handleVote(BufferedReader in) {
		String read = readIn(in);
		if (read.toLowerCase().equals("n")) return;
		System.out.println("Set flight's vote answer: (true/false)");
		read = readIn(in);
		boolean answer = Boolean.parseBoolean(read);
		proxy.setVote("flight", answer);
		System.out.println("Set car's vote answer: (true/false)");
		read = readIn(in);
		answer = Boolean.parseBoolean(read);
		proxy.setVote("car", answer);
		System.out.println("Set room's vote answer: (true/false)");
		read = readIn(in);
		answer = Boolean.parseBoolean(read);
		proxy.setVote("room", answer);
		System.out.println("Set customer's vote answer: (true/false)");
		read = readIn(in);
		answer = Boolean.parseBoolean(read);
		proxy.setVote("mw", answer);
		return;
	}
	
	private void automatedTransaction(int crashCase, String target) throws Exception{
		int id = proxy.start();
		String command = 
				target.equals("flight") ? "newFlight":
				target.equals("car") ? "newCar":
				target.equals("room") ? "newRoom":
				target.equals("customer") ? "customer":
				target.equals("mw") ? "mw" : ""; // Dunno what to put for mw
		Random random = new Random();
		int r1 = crashCase;
		int r2 = crashCase;
		int r3 = crashCase;
		print("Started new transaction: " + id);
		switch(command){
			case "newFlight":
			case "newCar":
			case "newRoom":
			case "customer":
				print("Running "+command+"(" + id +","+r1+","+r2+","+r3+")");
				proxy.addFlight(id, r1,r2,r3);
			
				print("Running "+command+"(" + id +","+r1+","+r2+","+r3+")");
				proxy.addCars(id, ""+r1,r2,r3);
			
				print("Running "+command+"(" + id +","+r1+","+r2+","+r3+")");
				proxy.addRooms(id, ""+r1,r2,r3);
			
				print("Running newCustomer(" + id + ")");
				int customer = proxy.newCustomer(id);
				print("Customer ID: " + customer);
				break;
			case "mw":
				print("Started new transaction: " + id);
				customer = proxy.newCustomer(id);
				print("Created new customer " + customer);
				print("Running newFlight(" + id +","+r1+","+r2+","+r3+")");
				proxy.addFlight(id,r1,r2,r3);
				print("Running newCar(" + id +",car"+r1+","+r2+","+r3+")");
				proxy.addCars(id,"car"+r1,r2,r3);
				print("Running newRoom(" + id +",room"+r1+","+r2+","+r3+")");
				proxy.addRooms(id,"room"+r1,r2,r3);

				print("Reserving flight...");
				proxy.reserveFlight(id, customer, r1);
				print("Reserving car...");
				proxy.reserveCar(id, customer, "car"+r1);
				print("Reserving room...");
				proxy.reserveRoom(id, customer, "room"+r2);
				break;
			default:	
				throw new IllegalStateException("Command ,"+command+", is not a valid command");
		}
		print("Committing transaction...");
		boolean result = proxy.commit(id);
		print("Result: "+result);
	}
	
	private String readIn(BufferedReader in) {
		String buffer = "";
		try{
			buffer = in.readLine();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return buffer;
	}
	
	private void run() {
		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
		print("Do test 1? (Y/n)");
		print("\t Crash coordinator before sending vote request");
		if (userSaidYes(stdin)) {
			try {
				proxy.crashPoint("mw", 1);
				int id = proxy.start();
				print("Started new transaction: " + id);
				int customer = proxy.newCustomer(id);
				print("Created new customer " + customer);
				print("Running newFlight(" + id +",2,2,2)");
				proxy.addFlight(id,2,2,2);
				print("Running newCar(" + id +",car2,2,2)");
				proxy.addCars(id,"car2",2,2);
				print("Running newRoom(" + id +",room2,2,2)");
				proxy.addRooms(id,"room2",2,2);
				print("Committing transaction...");

				print("Reserving flight...");
				proxy.reserveFlight(id, customer, 2);
				print("Reserving car...");
				proxy.reserveCar(id, customer, "car2");
				print("Reserving room...");
				proxy.reserveRoom(id, customer, "room2");

				boolean r = proxy.commit(id);
				print("Commit result: " + r);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		print("Do test 2? (Y/n)");
		print("\t Crash coordinator after sending vote request and before receiving any replies");
		if (userSaidYes(stdin)) {
			proxy.crashPoint("mw", 2);
			// TODO
		}
		print("Do test 3? (Y/n)");
		print("\t Crash coordinator after receiving some replies but not all");
		if (userSaidYes(stdin)) {
			try {
				proxy.crashPoint("mw", 3);
				int id = proxy.start();
				print("Started new transaction: " + id);
				int customer = proxy.newCustomer(id);
				print("Created new customer " + customer);
				print("Running newFlight(" + id +",3,3,3)");
				proxy.addFlight(id,3,3,3);
				print("Running newCar(" + id +",car3,3,3)");
				proxy.addCars(id,"car3",3,3);
				print("Running newRoom(" + id +",room3,3,3)");
				proxy.addRooms(id,"room3",3,3);

				print("Reserving flight...");
				proxy.reserveFlight(id, customer, 3);
				print("Reserving car...");
				proxy.reserveCar(id, customer, "car3");
				print("Reserving room...");
				proxy.reserveRoom(id, customer, "room3");

				print("Committing transaction...");
				boolean r = proxy.commit(id);
				print("Commit result: " + r);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		print("Do test 4? (Y/n)");
		print("\t Crash coordinator after receiving all replies but before deciding");
		if (userSaidYes(stdin)) {
			try {
				proxy.crashPoint("mw", 4);
				int id = proxy.start();
				print("Started new transaction: " + id);
				int customer = proxy.newCustomer(id);
				print("Created new customer " + customer);
				print("Running newFlight(" + id +",4,4,4)");
				proxy.addFlight(id,4,4,4);
				print("Running newCar(" + id +",car4,4,4)");
				proxy.addCars(id,"car4",4,4);
				print("Running newRoom(" + id +",room4,4,4)");
				proxy.addRooms(id,"room4",4,4);

				print("Reserving flight...");
				proxy.reserveFlight(id, customer, 4);
				print("Reserving car...");
				proxy.reserveCar(id, customer, "car4");
				print("Reserving room...");
				proxy.reserveRoom(id, customer, "room4");

				print("Committing transaction...");
				boolean r = proxy.commit(id);
				print("Commit result: " + r);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		print("Do test 5? (Y/n)");
		print("\t Crash coordinator after deciding but before sending decision");
		if (userSaidYes(stdin)) {
			try {
				proxy.crashPoint("mw", 5);
				int id = proxy.start();
				print("Started new transaction: " + id);
				int customer = proxy.newCustomer(id);
				print("Created new customer " + customer);
				print("Running newFlight(" + id +",5,5,5)");
				proxy.addFlight(id,5,5,5);
				print("Running newCar(" + id +",car5,5,5)");
				proxy.addCars(id,"car5",5,5);
				print("Running newRoom(" + id +",room5,5,5)");
				proxy.addRooms(id,"room5",5,5);

				print("Reserving flight...");
				proxy.reserveFlight(id, customer, 5);
				print("Reserving car...");
				proxy.reserveCar(id, customer, "car5");
				print("Reserving room...");
				proxy.reserveRoom(id, customer, "room5");

				print("Committing transaction...");
				boolean r = proxy.commit(id);
				print("Commit result: " + r);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		print("Do test 6? (Y/n)");
		print("\t Crash coordinator after sending some but not all decisions");
		if (userSaidYes(stdin)) {
			try {
				proxy.crashPoint("mw", 6);
				int id = proxy.start();
				print("Started new transaction: " + id);
				int customer = proxy.newCustomer(id);
				print("Created new customer " + customer);
				print("Running newFlight(" + id +",6,6,6)");
				proxy.addFlight(id,6,6,6);
				print("Running newCar(" + id +",car6,6,6)");
				proxy.addCars(id,"car6",6,6);
				print("Running newRoom(" + id +",room6,6,6)");
				proxy.addRooms(id,"room6",6,6);

				print("Reserving flight...");
				proxy.reserveFlight(id, customer, 6);
				print("Reserving car...");
				proxy.reserveCar(id, customer, "car6");
				print("Reserving room...");
				proxy.reserveRoom(id, customer, "room6");

				print("Committing transaction...");
				boolean r = proxy.commit(id);
				print("Commit result: " + r);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		print("Do test 7? (Y/n)");
		print("\t Crash coordinator after having sent all decisions");
		if (userSaidYes(stdin)) {
			try {
				proxy.crashPoint("mw", 7);
				int id = proxy.start();
				print("Started new transaction: " + id);
				int customer = proxy.newCustomer(id);
				print("Created new customer " + customer);
				print("Running newFlight(" + id +",7,7,7)");
				proxy.addFlight(id,7,7,7);
				print("Running newCar(" + id +",car7,7,7)");
				proxy.addCars(id,"car7",7,7);
				print("Running newRoom(" + id +",room7,7,7)");
				proxy.addRooms(id,"room7",7,7);

				print("Reserving flight...");
				proxy.reserveFlight(id, customer, 7);
				print("Reserving car...");
				proxy.reserveCar(id, customer, "car7");
				print("Reserving room...");
				proxy.reserveRoom(id, customer, "room7");

				print("Committing transaction...");
				boolean r = proxy.commit(id);
				print("Commit result: " + r);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		print("Do test 8? (Y/n)");
		print("\t Crash RM after receiving vote request but before sending answer");
		if (userSaidYes(stdin)) {
			String targetServer = getTarget(stdin);
			try {
				proxy.crashPoint(targetServer, 8);
				int id = proxy.start();
				print("Started new transaction: " + id);
				int customer;
				if (targetServer.equals("mw")) {
					customer = proxy.newCustomer(id);
					print("Created new customer " + customer);
				}
				else if (targetServer.equals("flight")) {
					print("Running newFlight(" + id +",7,7,7)");
					proxy.addFlight(id,7,7,7);
				}
				else if (targetServer.equals("car")) {
					print("Running newCar(" + id +",car7,7,7)");
					proxy.addCars(id,"car7",7,7);
				}
				else if (targetServer.equals("room")) {
					print("Running newRoom(" + id +",room7,7,7)");
					proxy.addRooms(id,"room7",7,7);
				}
				print("Committing transaction...");
				boolean r = proxy.commit(id);
				print("Commit result: " + r);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		print("Do test 9? (Y/n)");
		print("\t Crash RM after sending answer");
		if (userSaidYes(stdin)) {
			String targetServer = getTarget(stdin);
			proxy.crashPoint(targetServer, 9);
		}
		print("Do test 10? (Y/n)");
		print("\t Crash RM after receiving decision but before committing/aborting");
		if (userSaidYes(stdin)) {
			String targetServer = getTarget(stdin);
			proxy.crashPoint(targetServer, 10);
		}
	}
	// Read user input from stdin and returns true if user says yes (presses y or enter)
	private boolean userSaidYes(BufferedReader stdin) {
		String command;
		try {
			command = stdin.readLine();
			//remove heading and trailing white space
	        command = command.trim().toLowerCase();
	        if (command.equals("y") || command.length()==0) {
	        	return true;
	        }
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return false;
	}
	// Read user input from stdin for which server he/she wants to crash. 
	// Valid inputs are: mw, flight, car, room
	private String getTarget(BufferedReader stdin) {
		String input;
		String target = null;
		print("Which server to crash? (customer/flight/car/room)");
		try {
	        while (target == null) {
	        	input = stdin.readLine();
		        input = input.trim().toLowerCase();
		        if (input.toLowerCase().equals("customer") || input.toLowerCase().equals("flight") || input.toLowerCase().equals("car") || input.toLowerCase().equals("room")) {
		        	target = input;
		        } else {
		        	print("Invalid input.  Valid inputs are: mw, flight, car, room");
		        }
	        }
	        return target;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return target;
	}
	// Prints
	private void print(String msg) {
		System.out.println(msg);
	}
	
	
}
