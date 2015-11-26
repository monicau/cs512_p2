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

public class Logger {

	private Type type;
	public enum Type{
		flight("flight"), car("car"), room("room"), customer("mw"), coordinator("coordinator");
		Path location;
		private Type(String location) {
			this.location = Paths.get(location);
			this.location.toFile().mkdirs();
		}
	}
	public Logger(Type type) {
		this.type = type;
	}
	
	public void log(String msg){
		File log = this.type.location.resolve("log.txt").toFile();
		try {
			BufferedWriter br = new BufferedWriter(new FileWriter(log, true));
			br.write(msg +"\n");
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
