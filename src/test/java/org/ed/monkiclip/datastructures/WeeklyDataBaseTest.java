package org.ed.monkiclip.datastructures;

import junit.framework.TestCase;
import org.junit.Test;

import java.time.LocalDateTime;

public class WeeklyDataBaseTest extends TestCase {

    private WeeklyDataBase<String> map;

    @Override
    public void setUp() {
        map = new WeeklyDataBase<>();
    }

    @Test
    public void testAssignmentAndRetrieval() {
        LocalDateTime date = LocalDateTime.of(2021, 1, 13, 15, 0);
        map.putWeeklyData(date, "Foo");

        LocalDateTime differentDate = LocalDateTime.of(2021, 1, 15, 15, 0);
        String result = map.getWeeklyData(differentDate);

        assertNotNull(result);
        assertEquals("Foo", result);
    }

    @Test
    public void testRemoval() {
        LocalDateTime d1 = LocalDateTime.of(2021, 1, 13, 15, 0);
        LocalDateTime d2 = LocalDateTime.of(2021, 1, 20, 15, 0);
        LocalDateTime d3 = LocalDateTime.of(2021, 1, 27, 15, 0);
        map.putWeeklyData(d1, "Foo");
        map.putWeeklyData(d2, "Bar");
        map.putWeeklyData(d3, "Baz");

        map.removeElementsBefore(LocalDateTime.of(2021, 1, 19, 23, 59));

        assertFalse(map.containsWeeklyData(d1));
        assertTrue(map.containsWeeklyData(d2));
        assertTrue(map.containsWeeklyData(d3));
    }
}