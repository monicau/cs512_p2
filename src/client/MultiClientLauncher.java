package client;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.rmi.server.RMIClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.Callable;


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
	        System.out.println("Pick test type:\n\t1 = Single resource manager analysis\n\t2 = multiple resource manager analysis");
	        String testTypeStr = stdin.readLine().trim();
	        int testType = Integer.parseInt(testTypeStr);
	        
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
	        for (int j=0; j<numClients; j++) {
	        	ClientThread client = new ClientThread(testType, iterations, executionTime, tps, numClients, serviceName, serviceHost, servicePort);
	        	clients.add(client);
	        }
	        System.out.println("Launching clients..");
	        // Launch clients
	        for (int j=0; j<numClients; j++) {
	        	Thread t = new Thread(clients.get(j));
	        	threads.add(t);
	        	t.start();
	        }
	        // Wait for all threads to finish
	        for (int j=0; j<numClients; j++) {
	        	threads.get(j).join();
	        }
	        System.out.println("Received " + executionTime.size() + " results:");
	        for (long t : executionTime) {
	        	System.out.print(t + ", ");
	        }
	        System.out.println("");
	        executionTime.clear();
	        clients.clear();
	        threads.clear();
	        
//	        Thread resetter = (new Thread(new Resetter(serviceName, serviceHost, servicePort)));
//	        resetter.start();
//	        resetter.join();
	       
	        System.out.println("End of main guy.");
	        
        } catch(Exception e) {
        	System.out.println("Problem!!!");
            e.printStackTrace();
        }
	}
}
class ClientThread extends WSClient implements Runnable {

	int testType;
	int iterations;
	int tps;
	int numClients;
	long waitTime;
	Vector<Long> executionTimes;
	Random rand;
	
	public ClientThread(int testType, int iterations, Vector<Long> executionTimes, int tps, int numClients, String serviceName, String serviceHost, int servicePort) throws Exception {
		super(serviceName, serviceHost, servicePort);
		this.tps = tps;
		this.numClients = numClients;
		this.waitTime = (numClients / tps) * 1000;
		this.executionTimes = executionTimes;
		this.iterations = iterations;
		this.testType = testType;
		this.rand = new Random();
		System.out.println("wait time is " + waitTime + "ms");
	}
	
	@Override
	public void run() {
		switch (testType) {
		case 1:
			singleRM();
			break;
		case 2:
			multipleRM();
			break;
		}
	}
	
	// Do transactions that only access 1 RM
	private void singleRM() {
		long totalTime = 0;
		try {
			int r1 = rand.nextInt(1000);
			int r2 = rand.nextInt(1000);
			int r3 = rand.nextInt(1000);
			for (int i=0; i < iterations; i++) {
				long startTime = System.nanoTime();
				int id = proxy.start();
				proxy.addFlight(id, r1, r2, r3);
				proxy.addFlight(id, r1, r2, r3);
				proxy.addFlight(id, r1, r2, r3);
				proxy.commit(id);
				long endTime = System.nanoTime();
				totalTime = (endTime - startTime) / 1000000;
				System.out.println("Txn " + id + " added flight-" + r1 + " and committed. Took " + totalTime);
				long sleepTime = waitTime - totalTime;
				if (sleepTime < 0) {
					System.out.println("WARNING: System cannot handle " + tps + " transactions per second!");
					return;
				} else {
					executionTimes.add(totalTime);
					Thread.sleep(sleepTime);
				}
			}
		} catch (Exception e) {
			System.out.println("Deadlock or interrupted during sleep! Too bad");
			e.printStackTrace();
		}
	}
	
	public void client(int i, int[] r1s, int[] r2s, String[] r3s, String[] r4s, long[] totalTimes){
		try{
			long startTime = System.nanoTime();
			int id = proxy.start();
			proxy.addFlight(id, r1s[i], r2s[i], r2s[i]);
			proxy.addCars(id, r3s[i], r1s[i], r2s[i]);
			proxy.addRooms(id, r4s[i], r1s[i], r2s[i]);
			proxy.commit(id);
			long endTime = System.nanoTime();
			
			totalTimes[i] = (endTime - startTime) / 1000000;
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	// Do transactions that access all RM's
	private void multipleRM() {
		try {
			
			int iterations_todo = iterations + 20; // include warm-up / cool-down
			
			final int[] r1s = new int[iterations_todo];
			final int[] r2s = new int[iterations_todo];
			final String[] r3s = new String[iterations_todo];
			final String[] r4s = new String[iterations_todo];
			
			final long[] totalTimes = new long[iterations_todo];
			long[] randTimes = new long[iterations_todo];
			
			Thread[] submission = new Thread[iterations_todo];
			
			for (int i = 0; i < iterations_todo; i++) {
				r1s[i] = rand.nextInt(10000);
				r2s[i] = rand.nextInt(10000);
				r3s[i] = Integer.toString(rand.nextInt(10000));
				r4s[i] = Integer.toString(rand.nextInt(10000));
				randTimes[i] = (long) (rand.nextGaussian() * waitTime/10); //+/- 10% of waittime
			
				final ResourceManager rm = proxy;
				
				final int count = i;
				submission[i] = new Thread(()->{
					client(count, r1s, r2s, r3s, r4s, totalTimes);
				});
			}			
			for (int i=0; i < iterations_todo; i++) {
				submission[i].start();
				System.out.println("Running submission "+i);
				Thread.sleep(waitTime+randTimes[i]);
			}

			
			
			long[] goodOldTimes = Arrays.copyOfRange(totalTimes, 10, 10+iterations);
			long totalTime = Arrays.stream(goodOldTimes).reduce(0, (x,y) -> x+y );
			File f = new File("totalTimes-c"+numClients+"l"+tps+".csv");
			PrintWriter writer = new PrintWriter(f);
			writer.println("Average:");
			writer.println(totalTime+",\n");
			writer.println("Raw Data:");
			for (int i = 10; i < totalTimes.length-10; i++) {
				writer.println(totalTimes[i]+",");
			}
			writer.flush();
			writer.close();
			
		} catch (Exception e) {
			System.out.println("Deadlock or interrupted during sleep! Too bad");
			e.printStackTrace();
		}
	}
}