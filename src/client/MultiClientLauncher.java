package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Vector;

public class MultiClientLauncher {
	
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
	        
	        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
	        
	        // Read user input
	        System.out.println("How many clients?");
	        String numClientsStr = stdin.readLine().trim();
	        int numClients = Integer.parseInt(numClientsStr);
	        
	        System.out.println("How many transactions per second?");
	        String tpsStr = stdin.readLine().trim();
	        int tps = Integer.parseInt(tpsStr);
	        
	        System.out.println("How many iterations?");
	        String iterStr = stdin.readLine().trim();
	        int iterations = Integer.parseInt(iterStr);
	        
	        ArrayList<ClientThread> clients = new ArrayList<ClientThread>();
	        ArrayList<Thread> threads = new ArrayList<Thread>();
	        Vector<Long> executionTime = new Vector<Long>();
	        
	        System.out.println("Creating clients..");
	        // Create clients
	        for (int i=0; i<numClients; i++) {
	        	ClientThread client = new ClientThread(iterations, executionTime, tps, numClients, serviceName, serviceHost, servicePort);
	        	clients.add(client);
	        }
	        System.out.println("Launching clients..");
	        // Launch clients
	        for (int i=0; i<numClients; i++) {
	        	Thread t = new Thread(clients.get(i));
	        	threads.add(t);
	        	t.start();
	        }
	        // Wait for all threads to finish
	        for (int i=0; i<numClients; i++) {
	        	threads.get(i).join();
	        }
	        System.out.println("Received " + executionTime.size() + " results:");
	        for (long t : executionTime) {
	        	System.out.print(t + ", ");
	        }
	        System.out.println("End of main guy.");
	        
        } catch(Exception e) {
        	System.out.println("Problem!!!");
            e.printStackTrace();
        }
	}
}
class ClientThread extends WSClient implements Runnable {

	int iterations;
	int tps;
	int numClients;
	long waitTime;
	Vector<Long> executionTimes;
	
	public ClientThread(int iterations, Vector<Long> executionTimes, int tps, int numClients, String serviceName, String serviceHost, int servicePort) throws Exception {
		super(serviceName, serviceHost, servicePort);
		this.tps = tps;
		this.numClients = numClients;
		this.waitTime = numClients / tps;
		this.executionTimes = executionTimes;
		this.iterations = iterations;
	}
	
	@Override
	public void run() {
		System.out.println("Hi I'm a thread!");
		long totalTime = 0;
		try {
			for (int i=0; i<iterations; i++) {
				long startTime = System.nanoTime();
				int id = proxy.start();
				int customerID = proxy.newCustomer(id);
				long endTime = System.nanoTime();
				totalTime += (endTime - startTime) / 1000000;
			}
			executionTimes.add(totalTime/iterations);
		} catch (Exception e) {
			System.out.println("Deadlock! Too bad");
			e.printStackTrace();
		}
	}
	
}