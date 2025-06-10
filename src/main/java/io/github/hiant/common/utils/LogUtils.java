package io.github.hiant.common.utils;

import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * @author liudong.work@gmail.com Created at: 2025/6/10 16:11
 */
public class LogUtils {

    public static String getName(Logger log) {
        return log.getName();
    }


    public static boolean isTraceEnabled(Logger log) {
        return log.isTraceEnabled();
    }


    public static void trace(Logger log, String s) {
        log.trace(s);
    }


    public static void trace(Logger log, String s, Object o) {
        log.trace(s, o);
    }


    public static void trace(Logger log, String s, Object o, Object o1) {
        log.trace(s, o, o1);
    }


    public static void trace(Logger log, String s, Object... objects) {
        log.trace(s, objects);
    }


    public static void trace(Logger log, String s, Throwable throwable) {
        log.trace(s, throwable);
    }


    public static boolean isTraceEnabled(Logger log, Marker marker) {
        return log.isTraceEnabled(marker);
    }


    public static void trace(Logger log, Marker marker, String s) {
        log.trace(marker, s);
    }


    public static void trace(Logger log, Marker marker, String s, Object o) {
        log.trace(marker, s, o);
    }


    public static void trace(Logger log, Marker marker, String s, Object o, Object o1) {
        log.trace(marker, s, o, o1);
    }


    public static void trace(Logger log, Marker marker, String s, Object... objects) {
        log.trace(marker, s, objects);
    }


    public static void trace(Logger log, Marker marker, String s, Throwable throwable) {
        log.trace(marker, s, throwable);
    }


    public static boolean isDebugEnabled(Logger log) {
        return log.isDebugEnabled();
    }


    public static void debug(Logger log, String s) {
        log.debug(s);
    }


    public static void debug(Logger log, String s, Object o) {
        log.debug(s, o);
    }


    public static void debug(Logger log, String s, Object o, Object o1) {
        log.debug(s, o, o1);
    }


    public static void debug(Logger log, String s, Object... objects) {
        log.debug(s, objects);
    }


    public static void debug(Logger log, String s, Throwable throwable) {
        log.debug(s, throwable);
    }


    public static boolean isDebugEnabled(Logger log, Marker marker) {
        return log.isDebugEnabled(marker);
    }


    public static void debug(Logger log, Marker marker, String s) {
        log.debug(marker, s);
    }


    public static void debug(Logger log, Marker marker, String s, Object o) {
        log.debug(marker, s, o);
    }


    public static void debug(Logger log, Marker marker, String s, Object o, Object o1) {
        log.debug(marker, s, o, o1);
    }


    public static void debug(Logger log, Marker marker, String s, Object... objects) {
        log.debug(marker, s, objects);
    }


    public static void debug(Logger log, Marker marker, String s, Throwable throwable) {
        log.debug(marker, s, throwable);
    }


    public static boolean isInfoEnabled(Logger log) {
        return log.isInfoEnabled();
    }


    public static void info(Logger log, String s) {
        log.info(s);
    }


    public static void info(Logger log, String s, Object o) {
        log.info(s, o);
    }


    public static void info(Logger log, String s, Object o, Object o1) {
        log.info(s, o, o1);
    }


    public static void info(Logger log, String s, Object... objects) {
        log.info(s, objects);
    }


    public static void info(Logger log, String s, Throwable throwable) {
        log.info(s, throwable);
    }


    public static boolean isInfoEnabled(Logger log, Marker marker) {
        return log.isInfoEnabled(marker);
    }


    public static void info(Logger log, Marker marker, String s) {
        log.info(marker, s);
    }


    public static void info(Logger log, Marker marker, String s, Object o) {
        log.info(marker, s, o);
    }


    public static void info(Logger log, Marker marker, String s, Object o, Object o1) {
        log.info(marker, s, o, o1);
    }


    public static void info(Logger log, Marker marker, String s, Object... objects) {
        log.info(marker, s, objects);
    }


    public static void info(Logger log, Marker marker, String s, Throwable throwable) {
        log.info(marker, s, throwable);
    }


    public static boolean isWarnEnabled(Logger log) {
        return log.isWarnEnabled();
    }


    public static void warn(Logger log, String s) {
        log.warn(s);
    }


    public static void warn(Logger log, String s, Object o) {
        log.warn(s, o);
    }


    public static void warn(Logger log, String s, Object... objects) {
        log.warn(s, objects);
    }


    public static void warn(Logger log, String s, Object o, Object o1) {
        log.warn(s, o, o1);
    }


    public static void warn(Logger log, String s, Throwable throwable) {
        log.warn(s, throwable);
    }


    public static boolean isWarnEnabled(Logger log, Marker marker) {
        return log.isWarnEnabled(marker);
    }


    public static void warn(Logger log, Marker marker, String s) {
        log.warn(marker, s);
    }


    public static void warn(Logger log, Marker marker, String s, Object o) {
        log.warn(marker, s, o);
    }


    public static void warn(Logger log, Marker marker, String s, Object o, Object o1) {
        log.warn(marker, s, o, o1);
    }


    public static void warn(Logger log, Marker marker, String s, Object... objects) {
        log.warn(marker, s, objects);
    }


    public static void warn(Logger log, Marker marker, String s, Throwable throwable) {
        log.warn(marker, s, throwable);
    }


    public static boolean isErrorEnabled(Logger log) {
        return log.isErrorEnabled();
    }


    public static void error(Logger log, String s) {
        log.error(s);
    }


    public static void error(Logger log, String s, Object o) {
        log.error(s, o);
    }


    public static void error(Logger log, String s, Object o, Object o1) {
        log.error(s, o, o1);
    }


    public static void error(Logger log, String s, Object... objects) {
        log.error(s, objects);
    }


    public static void error(Logger log, String s, Throwable throwable) {
        log.error(s, throwable);
    }


    public static boolean isErrorEnabled(Logger log, Marker marker) {
        return log.isErrorEnabled(marker);
    }


    public static void error(Logger log, Marker marker, String s) {
        log.error(marker, s);
    }


    public static void error(Logger log, Marker marker, String s, Object o) {
        log.error(marker, s, o);
    }


    public static void error(Logger log, Marker marker, String s, Object o, Object o1) {
        log.error(marker, s, o, o1);
    }


    public static void error(Logger log, Marker marker, String s, Object... objects) {
        log.error(marker, s, objects);
    }


    public static void error(Logger log, Marker marker, String s, Throwable throwable) {
        log.error(marker, s, throwable);
    }
}
