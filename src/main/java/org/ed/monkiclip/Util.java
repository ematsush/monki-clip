package org.ed.monkiclip;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import static java.lang.String.format;

public class Util {
    private static final TemporalAdjuster SUNDAY_ADJUSTER = TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY);
    private static final TemporalAdjuster SATURDAY_ADJUSTER = TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY);

    public static LocalDateTime getSundayMorning(LocalDateTime date) {
        if (date == null) {
            throw new NullPointerException("Received null date");
        }
        return date.with(SUNDAY_ADJUSTER).toLocalDate().atTime(LocalTime.MIN);
    }

    public static LocalDateTime getSaturdayMidnight(LocalDateTime date) {
        if (date == null) {
            throw new NullPointerException("Received null date");
        }
        return date.with(SATURDAY_ADJUSTER).toLocalDate().atTime(LocalTime.MAX);
    }

    public static String convertToRfc3339(LocalDateTime d) {
        return format("%d-%d-%dT%02d:%02d:%02dZ", d.getYear(), d.getMonth().getValue(), d.getDayOfMonth(), d.getHour(), d.getMinute(), d.getSecond());
    }

}
