package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

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
	
	        client.run();
        } catch(Exception e) {
            e.printStackTrace();
        }
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
				print("Running newFlight(" + id +",2,2,2)");
				proxy.addFlight(id,2,2,2);
				print("Committing transaction...");
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
				print("Running newFlight(" + id +",3,3,3)");
				proxy.addFlight(id,3,3,3);
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
				print("Running newFlight(" + id +",4,4,4)");
				proxy.addFlight(id,4,4,4);
				print("Running newCar(" + id +",car4,4,4)");
				proxy.addCars(id,"car4",4,4);
				print("Running newRoom(" + id +",room4,4,4)");
				proxy.addRooms(id,"room4",4,4);
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
			proxy.crashPoint("mw", 5);
		}
		print("Do test 6? (Y/n)");
		print("\t Crash coordinator after sending some but not all decisions");
		if (userSaidYes(stdin)) {
			proxy.crashPoint("mw", 6);
		}
		print("Do test 7? (Y/n)");
		print("\t Crash coordinator after having sent all decisions");
		if (userSaidYes(stdin)) {
			proxy.crashPoint("mw", 7);
		}
		print("Do test 8? (Y/n)");
		print("\t Crash RM after receiving vote request but before sending answer");
		if (userSaidYes(stdin)) {
			String targetServer = getTarget(stdin);
			proxy.crashPoint(targetServer, 8);
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
		print("Which server to crash? (mw/flight/car/room)");
		try {
	        while (target == null) {
	        	input = stdin.readLine();
		        input = input.trim().toLowerCase();
		        if (input.toLowerCase().equals("mw") || input.toLowerCase().equals("flight") || input.toLowerCase().equals("car") || input.toLowerCase().equals("room")) {
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
