#cs512 Project deliverable 2 & 3

2: Add transactions!

3: Add 2 phase commit!

##Prerequisites
- JDK (8+)
- Apache Ant

###Resource Managers
```ant rm1 -Dservice.rm1.host=HOSTNAME -Dservice.rm1.port=PORT -Dmiddleware.ip=IP```

```ant rm2 -Dservice.rm2.host=HOSTNAME -Dservice.rm2.port=PORT -Dmiddleware.ip=IP```

```ant rm3 -Dservice.rm3.host=HOSTNAME -Dservice.rm3.port=PORT -Dmiddleware.ip=IP```

By default without arguments, the hostname is localhost, the ports are 8088, 8089, 8090.

To launch all 3 resource managers with default values, do ```ant rm```

Access via: http://HOSTNAME:PORT/rm/rm



###Middleware 
```ant server -Dservice.port=PORT```

will launch middleware and it will use default values to find the resource managers.  By default, middleware's port is 8080.

```ant server -Dservice.host=MYHOSTNAME -Dservice.rm1.host=HOSTNAME -Dservice.rm1.port=PORT -Dservice.rm2.host=HOSTNAME -Dservice.rm2.port=PORT -Dservice.rm3.host=HOSTNAME -Dservice.rm3.port=PORT```

will launch middleware with specified resource manager hostnames and port numbers.

Access it via: http://HOSTNAME:PORT/mw/service

###Client
```ant client -Dservice.host=HOST -Dservice.port=PORT```

where HOST is the hostname/IP of the middleware and PORT is the port that the middleware uses.

###Performance analysis
```ant analysis1 -Dservice.host=HOST -Dservice.port=PORT```

for single client system analysis, launch this client.

```ant analysis2 -Dservice.host=HOST -Dservice.port=PORT```

for multiple client system analysis, launch this client.

###How to crash things at client

``` crash,target ```

where target is mw, flight, car or room

``` crashAt,target,location ```

where location is one of the crash point numbers below

####Crash points

1 - Crash coordinator before sending vote request

2 - Crash coordinator after sending vote request and before receiving any replies

3 - Crash coordinator after receiving some replies but not all

4 - Crash coordinator after receiving all replies but before deciding

5 - Crash coordinator after deciding but before sending decision

6 - Crash coordinator after sending some but not all decisions

7 - Crash coordinator after having sent all decisions

8 - Crash RM after receiving vote request but before sending answer

9 - Crash RM after sending answer

10 - Crash RM after receiving decision but before committing/aborting

###How to change RM's vote answer

TODO

--

Refer to README.txt for more information
