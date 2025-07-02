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
        LocalDate inputDate = parseDate(dateStr, pattern);
        return inputDate != null && inputDate.isBefore(LocalDate.now());
    }

    /**
     * Check if the given date string represents a date after today.
     *
     * @param date    the date string to check
     * @param pattern the date pattern (e.g., "yyyy-MM-dd")
     * @return true if the date is after today, false otherwise (including invalid dates)
     */
    public static boolean isDateAfterToday(String date, String pattern) {
        LocalDate inputDate = parseDate(date, pattern);
        return inputDate != null && inputDate.isAfter(LocalDate.now());
    }

    /**
     * Check if the given date-time string represents a time before now.
     *
     * @param dateTime the date-time string to check
     * @param pattern  the date-time pattern (e.g., "yyyy-MM-dd HH:mm:ss")
     * @return true if the date-time is before now, false otherwise (including invalid dates)
     */
    public static boolean isDateTimeBeforeNow(String dateTime, String pattern) {
        LocalDateTime inputDateTime = parseDateTime(dateTime, pattern);
        return inputDateTime != null && inputDateTime.isBefore(LocalDateTime.now());
    }

    /**
     * Check if the given date-time string represents a time after now.
     *
     * @param dateTimeStr the date-time string to check
     * @param pattern     the date-time pattern (e.g., "yyyy-MM-dd HH:mm:ss")
     * @return true if the date-time is after now, false otherwise (including invalid dates)
     */
    public static boolean isDateTimeAfterNow(String dateTimeStr, String pattern) {
        LocalDateTime inputDateTime = parseDateTime(dateTimeStr, pattern);
        return inputDateTime != null && inputDateTime.isAfter(LocalDateTime.now());
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
                // Handles cases where the time range crosses midnight (e.g., 22:00 to 02:00)
                return !now.isBefore(start) || !now.isAfter(end);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format. Expected ISO_LOCAL_TIME ('HH:mm:ss')", e);
        }
    }

    /**
     * Parses a date string using the given pattern.
     *
     * @param date    the date string to parse
     * @param pattern the date pattern
     * @return the parsed LocalDate, or null if parsing fails
     */
    private static LocalDate parseDate(String date, String pattern) {
        if (date == null || pattern == null) {
            return null;
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parses a date-time string using the given pattern.
     *
     * @param dateTime the date-time string to parse
     * @param pattern  the date-time pattern
     * @return the parsed LocalDateTime, or null if parsing fails
     */
    private static LocalDateTime parseDateTime(String dateTime, String pattern) {
        if (dateTime == null || pattern == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
