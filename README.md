This package creates a GTFS-realtime feed for Amtrak trains from the same public Google Maps Engine dataset which powers Amtrak's new real-time map.

Building
========

Build with Maven:

`$ mvn clean install`

Copy config.sample to a suitable location; edit the file and set `MapsEngine.key` to a Google Maps Engine API key, and `GTFS.path` to the location of the Amtrak GTFS feed.

Running
=======

Run with:

`$ java -jar target/amtk-gtfsrealtime-1.0-SNAPSHOT-withAllDependencies.jar --config=myconfig`
