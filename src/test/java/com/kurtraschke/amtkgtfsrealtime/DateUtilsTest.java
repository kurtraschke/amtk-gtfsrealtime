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

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author kurt
 */
public class DateUtilsTest {

    public DateUtilsTest() {
    }

    @Test
    public void testParseTimestamp() throws Exception {
        System.out.println("parseTimestamp");
        String timestamp = "10/4/2013 10:00:34 AM";
        String timezone = "E";
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("US/Eastern"));
        cal.clear();
        cal.set(2013, 10 - 1, 4, 10, 0, 34);
        Date expResult = cal.getTime();
        Date result = DateUtils.parseTimestamp(timestamp, timezone);
        assertEquals(expResult, result);
    }

    @Test
    public void testParseStopTime() throws Exception {
        System.out.println("parseStopTime");
        String timestamp = "10/03/2013 04:33:00";
        String timezone = "P";
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("US/Pacific"));
        cal.clear();
        cal.set(2013, 10 - 1, 3, 4, 33, 0);
        Date expResult = cal.getTime();
        Date result = DateUtils.parseStopTime(timestamp, timezone);
        assertEquals(expResult, result);
    }
}