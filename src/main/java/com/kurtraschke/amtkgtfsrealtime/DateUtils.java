/*
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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

/**
 *
 * @author kurt
 */
public class DateUtils {

    public static ServiceDate serviceDateForTimestamp(String timestamp, String timezone) throws ParseException {
        Date parsedServiceDate = parseTimestamp(timestamp, timezone);
        ServiceDate serviceDate = new ServiceDate(parsedServiceDate);

        return serviceDate;
    }

    public static TimeZone timeZoneForCode(String timeZoneCode) {
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

    public static Date parseTimestamp(String timestamp, String timezone) throws ParseException {
        // 10/4/2013 10:00:34 AM
        TimeZone tz = timeZoneForCode(timezone);

        DateFormat sdf = new SimpleDateFormat("M/d/y h:m:s a");
        sdf.setTimeZone(tz);

        return sdf.parse(timestamp);
    }

    public static Date parseStopTime(String timestamp, String timezone) throws ParseException {
        // 10/03/2013 04:33:00
        TimeZone tz = timeZoneForCode(timezone);

        DateFormat sdf = new SimpleDateFormat("M/d/y H:m:s");
        sdf.setTimeZone(tz);

        return sdf.parse(timestamp);
    }
}
