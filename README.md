# geofence
Akka REST service to check Lat/Lon within a circle or polygon and in a time of day range

Operation comments
------------------

Requires java >= 8 and sbt i: https://www.scala-sbt.org/release/docs/Setup.html

Start server :

  sbt 'run localhost 8080 1,2,0'

  where host and port are supplied, the 3rd para being
        Lat,Lon,DataTime positions in file

To load example data :

  curl -F 'file=@example.csv.gz' http://localhost:8080/geofence/file
  {"filename":"example.csv.gz","success":true,"total":8142432}

to do a simple count of all records from the loaded file :

  curl -H "Content-type: application/json" -X POST -d '{"areaId": "example", "itemId": "MadeUpId"}' http://localhost:8080/geofence
  {"found":8142432,"message":"OK","success":true,"total":8142432}

to count a 10% sample with no conditions :

  curl -H "Content-type: application/json" -X POST -d '{"areaId": "example", "itemId": "MadeUpId", "sample": 0.1}'  http://localhost:8080/geofence
  {"found":814244,"message":"OK","success":true,"total":814244}

to select only those records in between 09:00:00 and 17:59:59 that occurred for any duration in the slot (inclusive of bounds) :

  curl -H "Content-type: application/json" -X POST -d '{"areaId": "example", "itemId": "MadeUpId", "slot": "09:00:00,17:59:59,0"}'  http://localhost:8080/geofence
  {"found":2622307,"message":"OK","success":true,"total":2622307}

to select only those records within a defined polygon area :

  curl -H "Content-type: application/json" -X POST -d '{"areaId": "example", "itemId": "MadeUpId", "condition": "(gpsInside(\"40.75561,-73.95228,40.77476,-73.99824,40.75942,-74.01206,40.73210,-74.01996,40.72749,-74.01532,40.70322,-74.02648,40.69697,-74.01695,40.71129,-73.97185,40.74082,-73.96618,40.75561,-73.95228\"))"}'  http://localhost:8080/geofence
  {"found":3252678,"message":"OK","success":true,"total":8142432}

to select only those records within a defined circular area (in km, m [miles], m [meters] and yd [yards], also available):
  curl -H "Content-type: application/json" -X POST -d '{"areaId": "example", "itemId": "MadeUpId", "condition": "(gpsInsideCircle(\"40.75561,-73.95228,5,k\"))"}'  http://localhost:8080/geofence
  {"found":4989268,"message":"OK","success":true,"total":8142432}

Note : the sampling and time span will narrow the total available records, but conditions will not. We are normally interested in the number of impressions in all the available records that the conditions restrict, so this is sensible. Also, the areaId is the stem of the name of the file being queried.

Any combination of condition, time span and sampling can be used.

Technical Comments
------------------

This system is highly tuned. The data is stored in time order and is indexed, so using time spans makes it much quicker. The data is stored internally in a direct ByteBuffer, i.e. in a very compact format. This allows even a modest machine, while keeping all data in memory, to use about 170 million records, composing up to 8 different datasets (at the moment, see below). Only a single CPU is use to scan a dataset, so concurrency is good. Data should be loaded/refreshed periodically. Concurrency and speed are good.

The conditions are the main overhead. On a modest machine with 16GB, to scan 8 million records and assuming queries are within time slots, the simple indexing reduces a full scan of 150 ms (1430 ms with shape condition) to a scan for one hour to 8ms (74 ms) [obviously it depends a bit on how busy the hour was, over the week/period, but that's the point]. So a modest polygon (say 8 verticies) or any size of circle will make this take an order of magnitude slower. There is also some latency for the calls, outside of the control of this system! Note: Per geo-coordinate Polygons are O(number of vertices), while circles are O(1) regardless of the circle size or postion on the earth.
