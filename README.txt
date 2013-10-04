Build with Maven:

$ mvn clean install

Copy config.sample to a suitable location; edit the file and set MapsEngine.key to a Google Maps Engine API key, and GTFS.path to the location of the Amtrak GTFS feed.

Run with:

$ java -jar target/amtk-gtfsrealtime-1.0-SNAPSHOT-withAllDependencies.jar --config=myconfig