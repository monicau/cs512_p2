package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Random;
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
		Random rand = new Random();
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
             int iterationsInt = Integer.parseInt(iterations) + 20;
             Vector<Long> executionTime = new Vector<Long>();
             switch (commandInt) {
             case 1:
            	 System.out.println("Single RM test");
            	 for (int i=0; i<iterationsInt; i++) {
	            	 try {
	            		 int r1 = rand.nextInt(10)+1;
	            		 int r2 = rand.nextInt(10)+1;
	            		 int r3 = rand.nextInt(10)+1;
		            	 boolean result = true;
		            	 long startTime = System.nanoTime();
		            	 int id = proxy.start();
		            	 result = proxy.addFlight(id, r1, r1, r1) && result;
		            	 result = proxy.addFlight(id, r2, r2, r2) && result;
		            	 result = proxy.addFlight(id, r3, r3, r3) && result;
		            	 if (result == false) {
		            		 System.out.println("Error creating customer/flights!  Exiting.");
		            		 proxy.abort(id);
		            		 break;
		            	 }
		            	 int customer = proxy.newCustomer(id);
		            	 result = proxy.reserveFlight(id, customer, r1) && result;
		            	 result = proxy.reserveFlight(id, customer, r2) && result;
		            	 result = proxy.reserveFlight(id, customer, r3) && result;
		            	 result = proxy.commit(id) && result;
		            	 if (result == false) {
		            		 System.out.println("Error reserving! Exiting");
		            		 break;
		            	 }
		            	 long endTime = System.nanoTime();
		            	 long duration = (endTime - startTime) / 1000000;
		            	 if (i>9 && i<(iterationsInt-10)) {
		            		 // Add execution time if we are not in warm up nor cool down
		            		 executionTime.add(duration);
		            	 }
	            	 } catch (Exception e) {
	            		 System.out.println("Deadlock!  Too bad.");
	            	 }
            	 }
            	 printTime(executionTime);
            	 printAverage(executionTime);
            	 break;
             case 2:
            	 System.out.println("Multiple RM test");
            	 for (int i=0; i<iterationsInt; i++) {
	            	 try {
	            		 int r1 = rand.nextInt(10)+1;
	            		 int r2 = rand.nextInt(10)+1;
	            		 int r3 = rand.nextInt(10)+1;
	            		 String str1 = Integer.toString(rand.nextInt(100000));
	            		 int id = proxy.start();
	            		 System.out.println("Starting txn " + id);
		            	 boolean result = true;
		            	 long startTime = System.nanoTime();
		            	 result = proxy.addFlight(id, r1, r2, r3) && result;
		            	 result = proxy.addCars(id, str1, r1, r2) && result;
		            	 result = proxy.addRooms(id, str1, r2, r3) && result;
		            	 if (result == false) {
		            		 System.out.println("Error creating customer/flight/car/room!  Exiting.");
		            		 proxy.abort(id);
		            		 break;
		            	 }
		            	 int customer = proxy.newCustomer(id);
		            	 result = proxy.reserveFlight(id, customer, r1) && result;
		            	 result = proxy.reserveCar(id, customer, str1) && result;
		            	 result = proxy.reserveRoom(id, customer, str1) && result;
		            	 result = proxy.commit(id) && result;
//		            	 System.out.println("End txn " + id + " flight ("+r1+"), car("+str1+"), room("+str1+")");
		            	 if (result == false) {
		            		 System.out.println("Error reserving itinerary! Exiting");
		            		 break;
		            	 }
		            	 long endTime = System.nanoTime();
		            	 long duration = (endTime - startTime) / 1000000;
		            	 if (i>9 && i<(iterationsInt-10)) {
		            		 // Add execution time if we are not in warm up nor cool down
		            		 executionTime.add(duration);
		            	 }
	            	 } catch (Exception e) {
	            		 System.out.println("Error!  Too bad.");
	            		 e.printStackTrace();
	            	 }
            	 }
            	 printTime(executionTime);
            	 printAverage(executionTime);
            	 break;
             }
         }
         catch (IOException io) {
             System.out.println("Unable to read from standard in");
             System.exit(1);
         }
		 System.out.println("Tests completed. Exiting...bye.");
	}
	private void printTime(Vector<Long> v) {
		System.out.println("Execution times:");
		for (long t : v) {
			System.out.print(t + ", ");
		}
	}
	private void printAverage(Vector<Long> v) {
		long sum = 0;
		for (long t : v) {
			sum += t;
		}
		long average = sum/v.size();
		System.out.println("Average: "+ average);
	}
}
