package main;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;

import org.apache.catalina.startup.Tomcat;


public class Main {

    public static void main(String[] args) 
    throws Exception {
    
        if (args.length != 9 && args.length != 13) {
            System.out.println(
                "Usage: java Main <use_services> <middleware ip> <service-name> <service-port> <deploy-dir> [<rm1-host> <rm1-port> <rm2-host> <rm2-port> <rm3-host> <rm3-port>] <service-type> <rm-type> <mw-name> <mw-port>");
            System.exit(-1);
        }
        
        
        String tcpPort = "";
        String serviceName = args[2];
        int port = Integer.parseInt(args[3]);
        String deployDir = args[4];
        String serviceType;
        if (args.length==9) {
        	serviceType = args[5];
        } else {
        	serviceType = args[11];
        	tcpPort = args[12];
        }
     
        String use_services = args[0];
        String middleware_ip = args[1];
        try(PrintWriter writer = new PrintWriter("config.txt", "UTF-8")){
        	writer.println(middleware_ip);
        	writer.println(port);
        	writer.close();
        }
        
        PrintWriter writer = new PrintWriter("serviceType.txt", "UTF-8");
        writer.println(serviceType);
        writer.println(tcpPort);
        writer.close();
        
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(deployDir);
        tomcat.enableNaming();
        
        tomcat.getHost().setAppBase(deployDir);
        tomcat.getHost().setDeployOnStartup(true);
        tomcat.getHost().setAutoDeploy(true);

        //tomcat.addWebapp("", new File(deployDir).getAbsolutePath());

        tomcat.addWebapp("/" + serviceName, 
                new File(deployDir + "/" + serviceName).getAbsolutePath());
        
        if (serviceName.equals("mw")) {
            //Add environment entries to web.xml to create rm proxies for middleware
	        String rmHost1 = args[5];
	        int rmPort1 = Integer.parseInt(args[6]);
	        String rmHost2 = args[7];
	        int rmPort2 = Integer.parseInt(args[8]);
	        String rmHost3 = args[9];
	        int rmPort3 = Integer.parseInt(args[10]);
	        
	        writer = new PrintWriter("rm.txt", "UTF-8");
	        writer.println(rmHost1);
	        writer.println(rmPort1);
	        writer.println(rmHost2);
	        writer.println(rmPort2);
	        writer.println(rmHost3);
	        writer.println(rmPort3);
	        writer.close();
        } else {
        	String rmType = args[6];
        	writer = new PrintWriter(rmType+".txt", "UTF-8");
        	writer.println(port);
        	writer.close();
        	
//        	String mwHost = args[7];
//        	String mwPort = args[8];
//        	writer = new PrintWriter("customer.txt", "UTF-8");
//        	writer.println(mwHost);
//        	writer.println(mwPort);
//        	writer.close();
        }
        Arrays.stream(args).forEach(str ->{
        	System.out.println(str);
        });
        tomcat.start();
        tomcat.getServer().await();
    }
    
}
