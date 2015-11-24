package server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
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
	
	// Write to storage using shadowing
	public void commitToStorage(RMMap data) {
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
			// Update master record
			updateMaster();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void updateMaster() {
		if (workingVersion == version.A) {
			workingVersion = version.B;
			committedVersion = version.A;
		} else {
			workingVersion = version.A;
			committedVersion = version.B;
		}
		// Point master record to the new committed version
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(name+"/master"))) {
			writer.write(""+committedVersion);
		} catch (IOException e) {
			System.out.println("S:: Fail to write to master record");
			e.printStackTrace();
		}
	}
}
