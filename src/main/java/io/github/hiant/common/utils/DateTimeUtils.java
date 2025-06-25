package io.github.hiant.common.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * @author liudong.work@gmail.com Created at: 2025/6/20 13:45
 */
public class DateTimeUtils {

    private DateTimeUtils() {
    }

    /**
     * Check if the given date-time string matches the specified pattern strictly.
     *
     * @param dateTime the date-time string to check
     * @param pattern  the date-time pattern (e.g., "yyyy-MM-dd")
     * @return true if the date-time string matches the pattern exactly, false otherwise
     */
    public static boolean isPatternMatch(String dateTime, String pattern) {
        if (dateTime == null || pattern == null || dateTime.isEmpty() || pattern.isEmpty()) {
            return false;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT)
                    .withLocale(Locale.ROOT);

            return dateTime.equals(formatter.format(formatter.parse(dateTime)));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the given date string represents a date before today.
     *
     * @param dateStr the date string to check
     * @param pattern the date pattern (e.g., "yyyy-MM-dd")
     * @return true if the date is before today, false otherwise (including invalid dates)
     */
    public static boolean isDateBeforeToday(String dateStr, String pattern) {
        if (dateStr == null || pattern == null) {
            return false;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT)
                    .withLocale(Locale.ROOT);

            LocalDate inputDate = LocalDate.parse(dateStr, formatter);
            LocalDate today = LocalDate.now();

            return inputDate.isBefore(today);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Check if the given date string represents a date after today.
     *
     * @param dateStr the date string to check
     * @param pattern the date pattern (e.g., "yyyy-MM-dd")
     * @return true if the date is after today, false otherwise (including invalid dates)
     */
    public static boolean isDateAfterToday(String dateStr, String pattern) {
        if (dateStr == null || pattern == null) {
            return false;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT)
                    .withLocale(Locale.ROOT);

            LocalDate inputDate = LocalDate.parse(dateStr, formatter);
            LocalDate today = LocalDate.now();

            return inputDate.isAfter(today);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Check if the given date-time string represents a time before now.
     *
     * @param dateTimeStr the date-time string to check
     * @param pattern     the date-time pattern (e.g., "yyyy-MM-dd HH:mm:ss")
     * @return true if the date-time is before now, false otherwise (including invalid dates)
     */
    public static boolean isDateTimeBeforeNow(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || pattern == null) {
            return false;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT)
                    .withLocale(Locale.ROOT);

            LocalDateTime inputDateTime = LocalDateTime.parse(dateTimeStr, formatter);
            LocalDateTime now = LocalDateTime.now();

            return inputDateTime.isBefore(now);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Check if the given date-time string represents a time after now.
     *
     * @param dateTimeStr the date-time string to check
     * @param pattern     the date-time pattern (e.g., "yyyy-MM-dd HH:mm:ss")
     * @return true if the date-time is after now, false otherwise (including invalid dates)
     */
    public static boolean isDateTimeAfterNow(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || pattern == null) {
            return false;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT)
                    .withLocale(Locale.ROOT);

            LocalDateTime inputDateTime = LocalDateTime.parse(dateTimeStr, formatter);
            LocalDateTime now = LocalDateTime.now();

            return inputDateTime.isAfter(now);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Check if the current time is between the specified start and end times (time part only, excluding date).
     *
     * @param startTime the start time string in ISO_LOCAL_TIME format ("HH:mm:ss")
     * @param endTime   the end time string in ISO_LOCAL_TIME format ("HH:mm:ss")
     * @return true if the current time is between startTime and endTime (inclusive if times are equal),
     * false otherwise
     * @throws IllegalArgumentException if startTime or endTime is invalid
     */
    public static boolean isNowTimeBetween(String startTime, String endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time cannot be null");
        }
        try {
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);
            LocalTime now = LocalTime.now();

            if (start.isBefore(end) || start.equals(end)) {
                return !now.isBefore(start) && !now.isAfter(end);
            } else {
                return !now.isBefore(start) || !now.isAfter(end);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format. Expected ISO_LOCAL_TIME ('HH:mm:ss')", e);
        }
    }
}
