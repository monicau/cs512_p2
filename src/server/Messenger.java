package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Messenger {

	ConnectionHandler connectionHandler;
	TriConsumer<String, Socket, OutputStream> onMessage = new TriConsumer<String, Socket, OutputStream>() {
		@Override
		public void accept(String message, Socket address, OutputStream os) {
			System.out.println("Received from "+address+" : "+ message );
		}
	};
	
	Map<Predicate<String>, Consumer<String>> eventHandlers = new HashMap<>(); 		// gets event callback once
	
	public Messenger(Socket socket) {
		new Thread(()->{
			try {
				System.out.println("Established connection on port "+socket.getPort());
				try{
					InputStream is = socket.getInputStream();
					BufferedReader br = new BufferedReader(new InputStreamReader(is));
					AtomicReference<String> in = new AtomicReference<>(br.readLine());
					OutputStream os = socket.getOutputStream();
					while(in.get() != null){
						Iterator<Entry<Predicate<String>, Consumer<String>>> iterator = eventHandlers.entrySet().iterator();
						while(iterator.hasNext()){
							Entry<Predicate<String>, Consumer<String>> next = iterator.next();
							if(next.getKey().test(in.get())){
								next.getValue().accept(in.get());
								iterator.remove();
							}
						}
						onMessage.accept(in.get(), socket, os);
						in.set(br.readLine());
					}
				}
				catch(IOException e){
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}).start();
	}
	
	// mw messenger
	public Messenger(int port) throws IOException {
		connectionHandler = new ConnectionHandler(port, new Consumer<Socket>() {
			@Override
			public void accept(Socket socket) {
				System.out.println("Accepted a new connection on port "+socket.getPort());
				try{
					InputStream is = socket.getInputStream();
					BufferedReader br = new BufferedReader(new InputStreamReader(is));
					AtomicReference<String> in = new AtomicReference<>(br.readLine());
					OutputStream os = socket.getOutputStream();
					while(in.get() != null){
						System.out.println("Messenger received "+in.get());
						
						Iterator<Entry<Predicate<String>, Consumer<String>>> iterator = eventHandlers.entrySet().iterator();
						System.out.println("Going through eventhandlers");
						while(iterator.hasNext()){
							Entry<Predicate<String>, Consumer<String>> next = iterator.next();
							System.out.println("Checking "+next);
							if(next.getKey().test(in.get())){
								System.out.println("using "+next+" on "+in.get());
								next.getValue().accept(in.get());
								iterator.remove();
							}
						}
						System.out.println("end going through");
						onMessage.accept(in.get(), socket, os);
						in.set(br.readLine());
					}
				}
				catch(IOException e){
					e.printStackTrace();
				}
			}
		});
	}
	
	public void start(){
		this.connectionHandler.start();
	}
	
	public void onMessage(TriConsumer<String, Socket, OutputStream> onMessage){
		this.onMessage = onMessage;
	}
}
