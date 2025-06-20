package io.github.hiant.common.utils;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * @author liudong.work@gmail.com Created at: 2025/6/20 13:45
 */
public class DateTimeUtils {

    private DateTimeUtils() {
    }

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

}
