package server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
	}
	
	// Returns data if master record exists
	public RMMap recover() {
		// Check if master record exists
		try {
			// Try to read from master record
			System.out.println("S:: trying to read from " + name + "/master");
			String targetVersionFile = new String(Files.readAllBytes(Paths.get(name+"/master")));
			System.out.println("S:: read this from master record: " + targetVersionFile);
			RMMap data;
			try {
				if (targetVersionFile.length() > 0) {
					// Read the right version file
					FileInputStream fileIn = new FileInputStream(name+"/"+targetVersionFile+".ser");
					ObjectInputStream in = new ObjectInputStream(fileIn);
					data = (RMMap) in.readObject();
					in.close();
					fileIn.close();
					// Update our reference of which is the committed version
					if (targetVersionFile.contains("A")) {
						committedVersion = version.A;
						workingVersion = version.B;
					} else {
						committedVersion = version.B;
						workingVersion = version.A;
					}
					System.out.println("S:: Recovery.. working version is now " + workingVersion);
					System.out.println("S:: Recovery.. committed version is now " + committedVersion);
					return data;
				} else {
					return null;
				}
			} catch (ClassNotFoundException e) {
				System.out.println("S:: fail to read data");
				e.printStackTrace();
			}
		} catch (IOException e) {
			System.out.println("S:: Fail to read master record.");
		}
		return null;
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
			e.printStackTrace();
		} catch (IOException e) {
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
			e.printStackTrace();
		}
	}
	
	// facilitator functions
	public Path dataFolder(){
		return Paths.get(this.name);
	}
	

	
}
