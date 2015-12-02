package server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Logger2PC {

	private Type type;
	public enum Type{
		flight("flight"), car("car"), room("room"), customer("mw"), coordinator("coordinator");
		Path location;
		private Type(String location) {
			this.location = Paths.get(location);
			this.location.toFile().mkdirs();
		}
	}
	public Logger2PC(Type type) {
		this.type = type;
	}
	
	public void log(String msg){
		File log = this.type.location.resolve("log.txt").toFile();
		System.out.println("Logger: Logging to " + type + "/log.txt: " + msg);
		try {
			BufferedWriter br = new BufferedWriter(new FileWriter(log, true));
			br.write(msg +"\n");
			br.close();
			System.out.println("Done logging.");
		} catch (FileNotFoundException e) {
			System.out.println("Logger: cannot find " + type + "/log.txt");
//			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Logger: cannot find " + type + "/log.txt");
//			e.printStackTrace();
		}
	}
	
}
