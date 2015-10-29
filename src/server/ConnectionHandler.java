package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class ConnectionHandler extends Thread{
	ServerSocket socket;
	Consumer<Socket> service;
	int timeout = 3000000;
	
	public ConnectionHandler(int port, Consumer<Socket> service) throws IOException {
		this.socket = new ServerSocket(port);
		this.service = service;
		System.out.println("Creating new connection handler");
	}

	public void setTimeout(int timeout){
		this.timeout = timeout;
	}
	
	@Override
	public void run() {
		System.out.println("Connection handling");
		while(!Thread.currentThread().isInterrupted()){
			try {
				final Socket incoming = this.socket.accept();
				incoming.setSoTimeout(timeout); // timeouts in 30s
				new Thread(new Runnable() {
					@Override
					public void run() {
						service.accept(incoming);
					}
				}).start();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}
