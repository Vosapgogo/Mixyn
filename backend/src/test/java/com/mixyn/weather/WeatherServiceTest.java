package com.mixyn.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mixyn.weather.WeatherService.Current;
import com.mixyn.weather.WeatherService.Daily;
import com.mixyn.weather.WeatherService.Forecast;
import com.mixyn.weather.WeatherService.Hourly;
import com.mixyn.weather.WeatherService.Outlook;

/**
 * Tests how one Open-Meteo answer becomes the weather page: the hours start at the current one, the
 * chance of rain counts only what is still to come today, and gaps in the answer don't break anything.
 * No network: the answers are built by hand.
 */
class WeatherServiceTest {

    /** Two days of hours, from 00:00 today; it rained at 08:00, it may rain at 19:00 and tomorrow at 10:00. */
    private static Hourly twoDays() {
        List<String> times = new ArrayList<>();
        List<Double> temperatures = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        List<Integer> rain = new ArrayList<>();
        List<Integer> day = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            times.add((i < 24 ? "2026-10-21T" : "2026-10-22T") + String.format("%02d:00", i % 24));
            temperatures.add(10.0 + i % 24 / 2.0);
            codes.add(3);
            rain.add(i == 8 ? 90 : i == 19 ? 60 : i == 34 ? 80 : 5);
            day.add(i % 24 >= 7 && i % 24 < 18 ? 1 : 0);
        }
        return new Hourly(times, temperatures, codes, rain, day);
    }

    private static Daily week() {
        List<String> dates = List.of("2026-10-21", "2026-10-22", "2026-10-23", "2026-10-24", "2026-10-25",
                "2026-10-26", "2026-10-27");
        return new Daily(dates, List.of(3, 61, 0, 2, 71, 95, 45), List.of(16.0, 14.0, 13.0, 12.0, 2.0, 11.0, 10.0),
                List.of(9.6, 10.0, 9.0, 8.0, -3.0, 7.0, 6.0), List.of(90, 80, 0, 10, 70, 60, 5),
                List.of(24.0, 12.0, 9.0, 8.0, 7.0, 40.0, 5.0), List.of(2.0, 1.0, 3.0, 2.0, 1.0, 1.0, 1.0));
    }

    @Test
    void hoursStartAtTheCurrentHour() {
        Current now = new Current("2026-10-21T15:45", 15.4, 14.1, 3, 8.7, 70, 1);
        Outlook outlook = WeatherService.outlook(new Forecast(now, twoDays(), week()), "Kyiv");

        assertEquals(WeatherService.HOURS, outlook.hours().size());
        assertEquals("2026-10-21T15:00", outlook.hours().get(0).time());
        assertEquals("2026-10-22T14:00", outlook.hours().get(WeatherService.HOURS - 1).time());
        assertTrue(outlook.hours().get(0).day());
        assertFalse(outlook.hours().get(4).day()); // 19:00
    }

    @Test
    void rainChanceCountsOnlyTheRestOfToday() {
        Current now = new Current("2026-10-21T15:45", 15.4, 14.1, 3, 8.7, 70, 1);
        Outlook outlook = WeatherService.outlook(new Forecast(now, twoDays(), week()), "Kyiv");

        // 08:00 (90%) is over and tomorrow's 80% is not today: what is left is 19:00.
        assertEquals(60, outlook.rainChance());
        assertEquals(15, outlook.temperature());
        assertEquals("cool", outlook.feel()); // feels like 14.1°
        assertEquals(9, outlook.wind());
        assertEquals(70, outlook.humidity());
        assertEquals(24, outlook.windMax());
    }

    @Test
    void daysComeWithTheirSky() {
        Current now = new Current("2026-10-21T15:45", 15.4, 14.1, 3, 8.7, 70, 1);
        Outlook outlook = WeatherService.outlook(new Forecast(now, twoDays(), week()), "Kyiv");

        assertEquals(7, outlook.days().size());
        assertEquals("rainy", outlook.days().get(1).sky());
        assertEquals("snowy", outlook.days().get(4).sky());
        assertEquals(-3, outlook.days().get(4).low());
        assertEquals(16, outlook.high());
        assertEquals(10, outlook.low());
    }

    @Test
    void gapsInTheAnswerDontBreakIt() {
        Current now = new Current("2026-10-21T15:45", 15.4, null, null, null, null, null);
        Hourly hourly = new Hourly(List.of("2026-10-21T15:00", "2026-10-21T16:00"), Arrays.asList(15.0, null),
                Arrays.asList(3, null), Arrays.asList(null, 40), null);
        Outlook outlook = WeatherService.outlook(new Forecast(now, hourly, null), "Kyiv");

        assertEquals(2, outlook.hours().size());
        assertEquals(40, outlook.rainChance());
        assertEquals("cloudy", outlook.hours().get(1).sky());
        assertTrue(outlook.days().isEmpty());
        assertEquals(15, outlook.high());
    }
}
