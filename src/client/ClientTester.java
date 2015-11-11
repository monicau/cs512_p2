package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;

public class ClientTester extends WSClient {
	public ClientTester(String serviceName, String serviceHost, int servicePort) throws Exception {
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
	
	        ClientTester client = new ClientTester(serviceName, serviceHost, servicePort);
	
	        client.run();
        } catch(Exception e) {
            e.printStackTrace();
        }
	}
	public void run() {
		
		
		
		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
		 try {
			 System.out.println("Enter the type of test to run:");
			 System.out.println("\t 1 = transactions on single resource manager");
			 System.out.println("\t 2 = transactions on all resource managers");
             //read the next command
             String command = stdin.readLine();
             //remove heading and trailing white space
             command = command.trim();
             int commandInt = Integer.parseInt(command);
             System.out.println("How many loops?");
             String iterations = stdin.readLine();
             iterations = iterations.trim();
             int iterationsInt = Integer.parseInt(iterations);
             switch (commandInt) {
             case 1:
            	 System.out.println("Single RM test");
            	 long averageTime = 0;
            	 int id1 = proxy.start();
            	 int id2 = proxy.start();
            	 for (int i=0; i<iterationsInt; i++) {
	            	 try {
		            	 boolean result = true;
		            	 long startTime = System.nanoTime();
		            	 proxy.newCustomerId(id1, 1);
		            	 result = proxy.addFlight(id1, 2, 2, 2) && result;
		            	 result = proxy.addFlight(id1, 3, 3, 3) && result;
		            	 result = proxy.addFlight(id1, 4, 4, 4) && result;
		            	 result = proxy.commit(id1) && result;
		            	 if (result == false) {
		            		 System.out.println("Error creating customer/flights!  Exiting.");
		            		 break;
		            	 }
		            	 result = proxy.reserveFlight(id2, 1, 2) && result;
		            	 result = proxy.reserveFlight(id2, 1, 3) && result;
		            	 result = proxy.reserveFlight(id2, 1, 4) && result;
		            	 result = proxy.commit(id2) && result;
		            	 if (result == false) {
		            		 System.out.println("Error reserving! Exiting");
		            		 break;
		            	 }
		            	 long endTime = System.nanoTime();
		            	 long duration = (endTime - startTime) / 1000000;
		            	 averageTime += duration;
		            	 System.out.println("Test duration (milliseconds): " + duration);
	            	 } catch (Exception e) {
	            		 System.out.println("Deadlock!  Too bad.");
	            	 }
            	 }
            	 averageTime = averageTime/iterationsInt;
            	 System.out.println("Average duration: " + averageTime);
            	 break;
             case 2:
            	 System.out.println("Multiple RM test");
            	 averageTime = 0;
            	 id1 = proxy.start();
            	 id2 = proxy.start();
            	 for (int i=0; i<iterationsInt; i++) {
	            	 try {
		            	 boolean result = true;
		            	 long startTime = System.nanoTime();
		            	 proxy.newCustomerId(id1, 1);
		            	 result = proxy.addFlight(id1, 2, 2, 2) && result;
		            	 result = proxy.addCars(id1, "boo", 3, 3) && result;
		            	 result = proxy.addRooms(id1, "boo", 4, 4) && result;
		            	 result = proxy.commit(id1) && result;
		            	 if (result == false) {
		            		 System.out.println("Error creating customer/flight/car/room!  Exiting.");
		            		 break;
		            	 }
		            	 Vector flights = new Vector();
		            	 flights.add("2");
		            	 result = proxy.reserveItinerary(id2, 1, flights, "boo", true, true) && result;
		            	 result = proxy.commit(id2) && result;
		            	 if (result == false) {
		            		 System.out.println("Error reserving itinerary! Exiting");
		            		 break;
		            	 }
		            	 long endTime = System.nanoTime();
		            	 long duration = (endTime - startTime) / 1000000;
		            	 averageTime += duration;
		            	 System.out.println("Test duration (milliseconds): " + duration);
	            	 } catch (Exception e) {
	            		 System.out.println("Deadlock!  Too bad.");
	            	 }
            	 }
            	 averageTime = averageTime/iterationsInt;
            	 System.out.println("Average duration: " + averageTime);
            	 break;
             }
         }
         catch (IOException io) {
             System.out.println("Unable to read from standard in");
             System.exit(1);
         }
		 System.out.println("Tests completed. Exiting...bye.");
	}
}
