#cs512 Project deliverable 2

Add transactions!

##Prerequisites
- JDK (8+)
- Apache Ant

###Resource Managers
```ant rm1 -Dservice.rm1.host=HOSTNAME -Dservice.rm1.port=PORT -Dservice.type=ws/tcp```

```ant rm2 -Dservice.rm2.host=HOSTNAME -Dservice.rm2.port=PORT -Dservice.type=ws/tcp```

```ant rm3 -Dservice.rm3.host=HOSTNAME -Dservice.rm3.port=PORT -Dservice.type=ws/tcp```

By default without arguments, the hostname is localhost, the ports are 8088, 8089, 8090, and service type is ws (web service). 

To launch all 3 resource managers with default values, do ```ant rm```

Access via: http://HOSTNAME:PORT/rm/rm



###Middleware 
```ant server -Dservice.port=PORT```

will launch middleware and it will use default values to find the resource managers.  By default, middleware's port is 8080 and service type is ws (web service).

```ant server -Dservice.rm1.host=HOSTNAME -Dservice.rm1.port=PORT -Dservice.rm2.host=HOSTNAME -Dservice.rm2.port=PORT -Dservice.rm3.host=HOSTNAME -Dservice.rm3.port=PORT -Dservice.type=ws/tcp -Dservice.tcp.port=PORT```

will launch middleware with specified resource manager hostnames and port numbers. Dservice.tcp.port is the starting port number that the middleware will assign to the resource managers.  After each port number is given, the middleware increases the port number by 1.  Default starting port is 8098.

Access it via: http://HOSTNAME:PORT/mw/service

###Client
```ant client -Dservice.host=HOST -Dservice.port=PORT -Dservice.type=ws/tcp```

Where HOST is the hostname/IP of the middleware and PORT is the port that the middleware uses. Service type is ws (web service) by default.

--

Refer to README.txt for more information
