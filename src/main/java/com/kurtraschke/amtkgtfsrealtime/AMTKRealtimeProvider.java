/*
 * Copyright (C) 2012 Google, Inc.
 * Copyright (C) 2013 Kurt Raschke
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.kurtraschke.amtkgtfsrealtime;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.VehiclePositions;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeIncrementalUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Singleton
public class AMTKRealtimeProvider {

    private static final Logger _log = LoggerFactory.getLogger(AMTKRealtimeProvider.class);
    private ScheduledExecutorService _executor;
    private GtfsRealtimeSink _vehiclePositionsSink;
    private GtfsRealtimeSink _tripUpdatesSink;
    private Map<String, Date> lastUpdateByVehicle = new HashMap<String, Date>();
    private GtfsDaoService _dao;
    /**
     * How often vehicle data will be downloaded, in seconds.
     */
    @Inject
    @Named("refreshInterval.vehicles")
    private int _vehicleRefreshInterval;
    @Inject
    @Named("MapsEngine.key")
    private String _mapsEngineKey;

    @Inject
    public void setVehiclePositionsSink(@VehiclePositions GtfsRealtimeSink sink) {
        _vehiclePositionsSink = sink;
    }

    @Inject
    public void setTripUpdateSink(@TripUpdates GtfsRealtimeSink sink) {
        _tripUpdatesSink = sink;
    }

    @Inject
    public void setGtfsDaoService(GtfsDaoService dao) {
        _dao = dao;
    }

    @PostConstruct
    public void start() {
        _log.info("Starting GTFS-realtime service");
        _executor = Executors.newSingleThreadScheduledExecutor();
        _executor.scheduleWithFixedDelay(new VehiclesRefreshTask(), 0,
                _vehicleRefreshInterval, TimeUnit.SECONDS);
    }

    /**
     * The stop method cancels the recurring vehicle data downloader task.
     */
    @PreDestroy
    public void stop() {
        _log.info("Stopping GTFS-realtime service");
        _executor.shutdownNow();
    }

    /**
     * This method downloads the latest vehicle data, processes each vehicle in
     * turn, and create a GTFS-realtime feed of trip updates and vehicle
     * positions as a result.
     */
    private void refreshVehicles() throws IOException, ParseException {
        /**
         * We download the vehicle details as an array of objects.
         */
        URL trainPositions = new URL("https://www.googleapis.com/mapsengine/v1/tables/01382379791355219452-08584582962951999356/features?version=published&maxResults=250&key=" + _mapsEngineKey);

        JsonParser parser = new JsonParser();
        JsonObject o = (JsonObject) parser.parse(new InputStreamReader(trainPositions.openStream()));

        JsonArray trains = o.getAsJsonArray("features");

        for (JsonElement e : trains) {
            try {
                JsonObject train = e.getAsJsonObject();

                JsonArray coordinates = train.getAsJsonObject("geometry").getAsJsonArray("coordinates");

                JsonObject trainProperties = train.getAsJsonObject("properties");

                if (!trainProperties.get("TrainState").getAsString().equals("Active")) {
                    continue;
                }


                String trainNumber = trainProperties.get("TrainNum").getAsString();

                String heading = trainProperties.get("Heading").getAsString();

                float velocity = 0;
                
                if (trainProperties.has("Velocity") && !trainProperties.get("Velocity").getAsString().equals("")) {
                     velocity = trainProperties.get("Velocity").getAsFloat();
                }

                String originTimestamp = trainProperties.get("OrigSchDep").getAsString();
                String originTimezone = trainProperties.get("OriginTZ").getAsString();

                String updateTimestamp = trainProperties.get("LastValTS").getAsString();
                String updateTimezone = trainProperties.get("EventTZ").getAsString();

                String key = originTimestamp + "-" + originTimezone + "-" + trainNumber;

                /**
                 * We construct a TripDescriptor and VehicleDescriptor, which
                 * will be used in both trip updates and vehicle positions to
                 * identify the trip and vehicle.
                 */
                TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();

                String tripId = tripForTrainAndDate(trainNumber, originTimestamp, originTimezone);
                if (tripId == null) {
                    continue;
                }

                tripDescriptor.setTripId(tripId);
                Date parsedServiceDate = parseTimestamp(originTimestamp, originTimezone);
                ServiceDate serviceDate = new ServiceDate(parsedServiceDate);
                tripDescriptor.setStartDate(String.format("%04d%02d%02d", serviceDate.getYear(), serviceDate.getMonth(), serviceDate.getDay()));


                VehicleDescriptor.Builder vehicleDescriptor = VehicleDescriptor.newBuilder();
                vehicleDescriptor.setId(key);
                vehicleDescriptor.setLabel(trainNumber);

                TripUpdate.Builder tripUpdate = TripUpdate.newBuilder();

                for (Entry<String, JsonElement> propEntry : trainProperties.entrySet()) {
                    if (propEntry.getKey().startsWith("Station")) {
                        StopTimeUpdate stu = stopTimeUpdateForStation(propEntry.getValue().getAsString());
                        if (stu != null) {
                            tripUpdate.addStopTimeUpdate(stu);
                        }
                    }
                }

                tripUpdate.setTrip(tripDescriptor);
                tripUpdate.setVehicle(vehicleDescriptor);
                tripUpdate.setTimestamp(parseTimestamp(updateTimestamp, updateTimezone).getTime() / 1000L);
                /**
                 * Create a new feed entity to wrap the trip update and add it
                 * to the GTFS-realtime trip updates feed.
                 */
                FeedEntity.Builder tripUpdateEntity = FeedEntity.newBuilder();
                tripUpdateEntity.setId(key);
                tripUpdateEntity.setTripUpdate(tripUpdate);

                /**
                 * To construct our VehiclePosition, we create a position for
                 * the vehicle. We add the position to a VehiclePosition
                 * builder, along with the trip and vehicle descriptors.
                 */
                Position.Builder position = Position.newBuilder();
                position.setLatitude(coordinates.get(1).getAsFloat());
                position.setLongitude(coordinates.get(0).getAsFloat());
                position.setBearing(degreesForHeading(heading));
                position.setSpeed(velocity * 0.44704f);

                VehiclePosition.Builder vehiclePosition = VehiclePosition.newBuilder();
                vehiclePosition.setTimestamp(parseTimestamp(updateTimestamp, updateTimezone).getTime() / 1000L);
                vehiclePosition.setPosition(position);
                vehiclePosition.setTrip(tripDescriptor);
                vehiclePosition.setVehicle(vehicleDescriptor);

                /**
                 * Create a new feed entity to wrap the vehicle position and add
                 * it to the GTFS-realtime vehicle positions feed.
                 */
                FeedEntity.Builder vehiclePositionEntity = FeedEntity.newBuilder();
                vehiclePositionEntity.setId(key);
                vehiclePositionEntity.setVehicle(vehiclePosition);

                GtfsRealtimeIncrementalUpdate tripUpdateUpdate = new GtfsRealtimeIncrementalUpdate();
                tripUpdateUpdate.addUpdatedEntity(tripUpdateEntity.build());
                _tripUpdatesSink.handleIncrementalUpdate(tripUpdateUpdate);

                GtfsRealtimeIncrementalUpdate vehiclePositionUpdate = new GtfsRealtimeIncrementalUpdate();
                vehiclePositionUpdate.addUpdatedEntity(vehiclePositionEntity.build());
                _vehiclePositionsSink.handleIncrementalUpdate(vehiclePositionUpdate);
            } catch (Exception ex) {
                _log.warn("Exception processing vehicle", ex);
            }

        }

    }

    /**
     * Task that will download new vehicle data from the remote data source when
     * executed.
     */
    private class VehiclesRefreshTask implements Runnable {

        @Override
        public void run() {
            try {
                _log.info("Refreshing vehicles");
                refreshVehicles();
            } catch (Exception ex) {
                _log.warn("Error in vehicle refresh task", ex);
            }
        }
    }

    private TimeZone timeZoneForCode(String timeZoneCode) {
        switch (timeZoneCode) {
            case "E":
                return TimeZone.getTimeZone("US/Eastern");
            case "C":
                return TimeZone.getTimeZone("US/Central");
            case "M":
                return TimeZone.getTimeZone("US/Mountain");
            case "P":
                return TimeZone.getTimeZone("US/Pacific");
            default:
                throw new IllegalArgumentException("Unknown timezone: " + timeZoneCode);
        }
    }

    private int degreesForHeading(String heading) {
        switch (heading) {
            case "N":
                return 0;
            case "NE":
                return 45;
            case "E":
                return 90;
            case "SE":
                return 135;
            case "S":
                return 180;
            case "SW":
                return 225;
            case "W":
                return 270;
            case "NW":
                return 315;
            default:
                throw new IllegalArgumentException("Unknown heading: " + heading);
        }
    }

    private String tripForTrainAndDate(final String trainNumber, String originTimestamp, String originTimezone) throws ParseException {
        Date parsedServiceDate = parseTimestamp(originTimestamp, originTimezone);
        ServiceDate serviceDate = new ServiceDate(parsedServiceDate);

        Set<AgencyAndId> serviceIds = _dao.getCalendarServiceData().getServiceIdsForDate(serviceDate);

        List<Iterable<Trip>> trips = new ArrayList<>();

        for (AgencyAndId serviceId : serviceIds) {
            trips.add(_dao.getGtfsRelationalDao().getTripsForServiceId(serviceId));
        }

        Iterable<Trip> allTrips = Iterables.concat(trips);

        Trip theTrip;

        try {
            theTrip = Iterables.find(allTrips, new Predicate<Trip>() {
                @Override
                public boolean apply(Trip t) {
                    return t.getTripShortName().equals(trainNumber);
                }
            });
        } catch (NoSuchElementException ex) {
            _log.warn("Could not find train " + trainNumber + " departing on date " + serviceDate.toString());
            return null;
        }

        return theTrip.getId().getId();

    }

    private Date parseTimestamp(String timestamp, String timezone) throws ParseException {
        // 10/4/2013 10:00:34 AM
        TimeZone tz = timeZoneForCode(timezone);

        DateFormat sdf = new SimpleDateFormat("M/d/y h:m:s a");
        sdf.setTimeZone(tz);

        return sdf.parse(timestamp);
    }

    private Date parseStopTime(String timestamp, String timezone) throws ParseException {
        // 10/03/2013 04:33:00
        TimeZone tz = timeZoneForCode(timezone);

        DateFormat sdf = new SimpleDateFormat("M/d/y H:m:s");
        sdf.setTimeZone(tz);

        return sdf.parse(timestamp);
    }

    private StopTimeUpdate stopTimeUpdateForStation(String stationJson) throws ParseException {
        JsonParser parser = new JsonParser();
        JsonObject o = (JsonObject) parser.parse(stationJson);


        StopTimeUpdate.Builder b = StopTimeUpdate.newBuilder();

        b.setStopId(o.get("code").getAsString());

        if (o.has("postarr") && o.has("postdep")) {

            StopTimeEvent.Builder arr = StopTimeEvent.newBuilder();
            arr.setTime(parseStopTime(o.get("postarr").getAsString(), o.get("tz").getAsString()).getTime() / 1000L);

            StopTimeEvent.Builder dep = StopTimeEvent.newBuilder();
            dep.setTime(parseStopTime(o.get("postdep").getAsString(), o.get("tz").getAsString()).getTime() / 1000L);

            b.setArrival(arr);
            b.setDeparture(dep);

        } else if (o.has("estarr")) {
            StopTimeEvent.Builder arr = StopTimeEvent.newBuilder();
            arr.setTime(parseStopTime(o.get("estarr").getAsString(), o.get("tz").getAsString()).getTime() / 1000L);

            b.setArrival(arr);

            if (o.has("estdep")) {
                StopTimeEvent.Builder dep = StopTimeEvent.newBuilder();
                dep.setTime(parseStopTime(o.get("estdep").getAsString(), o.get("tz").getAsString()).getTime() / 1000L);

                b.setDeparture(dep);
            }
        }

        if (b.hasArrival() || b.hasDeparture()) {
            return b.build();
        } else {
            return null;
        }
    }
}
