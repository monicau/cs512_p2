package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.MalformedURLException;

import server.Messenger;
import server.Trace;
import client.ResourceManager;


public class WSClient {

//    ResourceManagerImplService service;
   	MiddlewareImplService service;
    
    ResourceManager proxy;
    TCPClient tcp;
    boolean useWebService;

	private Messenger middlewareIn;

	private Socket s;

	private InputStream inputStream;

	private OutputStream outputStream;
    
    public WSClient(String serviceName, String serviceHost, int servicePort) 
    throws MalformedURLException {
    	
    	//Determine if we are using web services or tcp
    	try {
    		BufferedReader reader = new BufferedReader(new FileReader(new File("serviceType.txt")));
    		try {
    			String line = reader.readLine();
    			if (line.equals("ws")) {
    				useWebService = true;
    			} else {
    				useWebService = false;
    			}
    		} catch (IOException e) {
    			Trace.info("ERROR: IOException, cannot read serviceType.txt");
    		}
    	} catch (FileNotFoundException e) {
    		Trace.info("ERROR: Cannot find serviceType.txt");
    	}
    	if (useWebService) {
	        URL wsdlLocation = new URL("http", serviceHost, servicePort, 
	                "/" + serviceName + "/service?wsdl");
	        service = new MiddlewareImplService(wsdlLocation);
	        proxy = service.getMiddlewareImplPort();
    	} else {
    		try{
    			s = new Socket(serviceHost, 9090);
        		outputStream = s.getOutputStream();
				middlewareIn = new Messenger(s);
				tcp = new TCPClient(middlewareIn, outputStream);
    		}
    		catch(Exception e){
    			e.printStackTrace();
    		}
    		
    	}
    }

}
