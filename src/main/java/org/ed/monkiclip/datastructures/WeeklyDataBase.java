package org.ed.monkiclip.datastructures;

import org.ed.monkiclip.Util;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeeklyDataBase<E> {

    private final Map<LocalDateTime, E> map;

    public WeeklyDataBase() {
        map = new HashMap<>();
    }

    public boolean containsWeeklyData(LocalDateTime date) {
        return map.containsKey(Util.getSaturdayMidnight(date));
    }

    public E putWeeklyData(LocalDateTime date, E e) {
        if (containsWeeklyData(date)) {
            throw new IllegalStateException("Attempting to put data for week that exists");
        }
        return map.put(Util.getSaturdayMidnight(date), e);
    }

    public E getWeeklyData(LocalDateTime date) {
        if (!containsWeeklyData(date)) {
            throw new IllegalArgumentException("No elements for this date.");
        }
        return map.get(Util.getSaturdayMidnight(date));
    }

    public void removeElementsBefore(LocalDateTime date) {
        List<LocalDateTime> datesToRemove = new ArrayList<>();
        for (LocalDateTime d : map.keySet()) {
            if (d.compareTo(date) < 0) {
                datesToRemove.add(d);
            }
        }
        for (LocalDateTime d : datesToRemove) {
            map.remove(d);
        }
    }
}
