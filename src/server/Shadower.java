package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.StringTokenizer;

/**
 * 
 *  Is responsible for shadowing.
 *  Each resource manager's set of files is located in
 *  the directory of its name: mw, flight, car, room
 */
public class Shadower {
	public enum version { A, B };
	
	private version workingVersion;
	private version committedVersion;
	String name;
	
	protected String MW_LOCATION;
	
	
	public Shadower(String name) {
		this.name = name;
		
		// Create folder for this resource manager if it doesn't exist already
		File dir = new File(name);
		if (!dir.exists()) {
			System.out.println("S:: creating directory called " + name);
			try {
				dir.mkdir();
			} catch (SecurityException e) {
				System.out.println("S:: error creating directory! ");
			}
		}
		
		
		try(BufferedReader reader = new BufferedReader(new FileReader(new File("config.txt")))) {
			MW_LOCATION = reader.readLine();
			System.out.println("Setting middleware host as: " + MW_LOCATION);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	// Returns data if master record exists
	public RMMap recover() {
		// Set working and committed version files
		File masterFile = new File(name+"/master");
		if (!masterFile.exists()) {
			// If master file doesn't exist, set working version as A
			System.out.println("S:: master file does not exist.  Setting A as working version");
			workingVersion = version.A;
		} else {
			try {
				System.out.println("S:: trying to read from " + name + "/master");
				String targetVersionFile = new String(Files.readAllBytes(Paths.get(name+"/master")));
				if (targetVersionFile.contains("A")) {
					System.out.println("S:: committed version = A, working version = B");
					committedVersion = version.A;
					workingVersion = version.B;
				} else {
					System.out.println("S:: committed version = B, working version = A");
					committedVersion = version.B;
					workingVersion = version.A;
				}
			} catch (IOException e) {
			}
		}
		
		// Check if log exists.  This would affect which version file to recover from
		Path logPath = null;
		try {
			logPath = Paths.get(name + "/log.txt");
		} catch (InvalidPathException e) {
			System.out.println("S:: Cannot find " + name + "/log.txt");
//			e.printStackTrace();
		}
		try {
			List<String> allLines = Files.readAllLines(logPath);
			int n = allLines.size();
			for (int i = 0; i < n; i++) {
				String line = allLines.get(i);
				String[] split = line.split(",");
				int txn = Integer.parseInt(split[0]);
				System.out.println("S:: Log finds start of txn " + txn);

				switch(split.length){
				case 2:
					// RM replied but did not receive decision
					// If its vote was yes, check if we received decision
					if (Boolean.parseBoolean(split[1])) {
						int id = Integer.parseInt(split[0]);
						// Check next line to see if it received decision
						if (i+1 < n) {
							String nextLine = allLines.get(i+1);
							String[] splitNextLine = nextLine.split(",");
							int nextId = Integer.parseInt(splitNextLine[0]);
							if (nextId != id) {
								// TODO: ask coordinator about decision for this txn
								askCoordinator(id);
							}
						}
						else{
							askCoordinator(id);

						}
					}
					break;
				case 3:
					// RM replied and received decision. Do nothing.
					break;
				}
			}
		} catch (IOException e) {
			System.out.println("S:: Cannot find " + name + "/log.txt");
//			e.printStackTrace();
		}
		// the incomplete logs are no longer valid so delete them
		try {
			System.out.println("Deleting " + logPath.toString());
			Files.deleteIfExists(logPath);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		// Try to recover data from version file 
		try {
			RMMap data;
			String targetVersionFile = committedVersion.toString();
			try {
				// Read the right version file
				System.out.println("Recovering from " + name+"/"+targetVersionFile+".ser");
				FileInputStream fileIn = new FileInputStream(name+"/"+targetVersionFile+".ser");
				ObjectInputStream in = new ObjectInputStream(fileIn);
				data = (RMMap) in.readObject();
				in.close();
				fileIn.close();
				System.out.println("S:: Recovery.. working version is now " + workingVersion);
				System.out.println("S:: Recovery.. committed version is now " + committedVersion);
				return data;
			} catch (ClassNotFoundException e) {
				System.out.println("S:: fail to read data");
//				e.printStackTrace();
			}
		} catch (IOException e) {
			System.out.println("S:: Fail to read master record.");
		} catch (NullPointerException e) {
			System.out.println("S:: Fail to read master record.");
		}
		return null;
	}



	private void askCoordinator(int id) throws UnknownHostException, IOException {
		Socket coordinator = new Socket(MW_LOCATION, 9999);
		PrintWriter writer = new PrintWriter(coordinator.getOutputStream());
		writer.println(id);
		writer.flush();
		BufferedReader br = new BufferedReader(new InputStreamReader(coordinator.getInputStream()));
		String in = br.readLine();
		boolean decision = Boolean.parseBoolean(in);
		System.out.println("S::The incomplete transaction was committed :"+decision);
		coordinator.close();
		if(decision){
			actualCommit();
		}
	}
	
	public int recoverTxnID() {
		try {
			String txn = new String(Files.readAllBytes(Paths.get("mw/txnCounter.txt")));
			if (txn != null) {
				txn = txn.replaceAll("\\s", "");
				int txnNum = Integer.parseInt(txn);
				return txnNum;
			}
		} catch (IOException e) {
			System.out.println("S:: Fail to read txn counter.");
		}
		return -1;
	}
	
	// Write to storage using shadowing
	public void prepareCommit(RMMap data) {
		// Write data to a version file
		if (workingVersion == null) {
			workingVersion = version.A;
		}
		System.out.println("S:: writing data to version " + workingVersion);
		try {
			// Write data 
			FileOutputStream fileOut = new FileOutputStream(name+"/"+workingVersion+".ser");
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			System.out.println("S:: writing data now...");
			out.writeObject(data);
			fileOut.close();
			out.close();
			
		} catch (FileNotFoundException e) {
			System.out.println("S:: Cannot find " + name + "/"+workingVersion+".ser");
//			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("S:: Cannot find " + name + "/"+workingVersion+".ser");
			e.printStackTrace();
		}
	}
	
	public void latestTxn(int txn) {
		PrintWriter writer;
		try {
			writer = new PrintWriter("mw/txnCounter.txt", "UTF-8");
			writer.println(txn);
	        writer.close();
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			System.out.println("S:: Fail to write to mw/txnCounter.txt");
			e.printStackTrace();
		}
	}
	
	// update the master to point to the current working data file
	public void actualCommit() {
		System.out.println("S:: Actual commit called ... ");
		
		if (workingVersion == version.A) {
			workingVersion = version.B;
			committedVersion = version.A;
		} else {
			workingVersion = version.A;
			committedVersion = version.B;
		}
		System.out.println("S:: working version is now " + workingVersion);
		System.out.println("S:: committed version is now " + committedVersion);
		
		// Point master record to the new committed version
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(name+"/master"))) {
			System.out.println("S:: writing version " + committedVersion + " to " + name +" /master");
			writer.write(""+committedVersion);
		} catch (IOException e) {
			System.out.println("S:: Fail to write to master record");
//			e.printStackTrace();
		}
	}
	
	// facilitator functions
	public Path dataFolder(){
		return Paths.get(this.name);
	}
	

	
}
