package io.github.hiant.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class for handling IP addresses and network-related operations.
 * <p>
 * This class provides methods to retrieve client IP address from HTTP requests,
 * get local host information, simplify IPv6 addresses, etc.
 * </p>
 */
@Slf4j
public class IpUtils {
    private static final String UNKNOWN = "unknown";

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private IpUtils() {
    }

    /**
     * Retrieves the client's remote IP address from the HTTP request.
     * <p>
     * Tries multiple headers like X-Forwarded-For, Proxy-Client-IP, etc.,
     * before falling back to {@link HttpServletRequest#getRemoteAddr()}.
     * </p>
     *
     * @param request The HTTP servlet request.
     * @return The simplified IP address string, or null if not found.
     */
    public static String getRemoteAddr(HttpServletRequest request) {
        String ip = null;
        try {
            ip = request.getHeader("x-forwarded-for");
            if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.warn("getIpAddr error", e);
        }
        return fullySimplifyIP(ip);
    }

    /**
     * Gets the hostname of the local machine.
     *
     * @param defaultHostName A default hostname to use in case of failure.
     * @return The hostname of the local machine.
     * @throws IllegalStateException if the hostname cannot be determined and no default is provided.
     */
    public static String hostname(String defaultHostName) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostName();
        } catch (UnknownHostException e) {
            if (defaultHostName != null) {
                return defaultHostName;
            }
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the first valid IPv4 address of the local machine.
     * If none is found, returns loopback address "127.0.0.1".
     *
     * @return An IPv4 address string.
     */
    public static String ipv4() {
        List<String> allIp = allIp();
        return allIp.isEmpty() ? "127.0.0.1" : allIp.get(0);
    }

    /**
     * Retrieves all site-local, non-loopback IPv4/IPv6 addresses of the machine.
     *
     * @return A list of IP address strings.
     */
    public static List<String> allIp() {
        List<String> list = new LinkedList<>();
        try {
            Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
            while (enumeration.hasMoreElements()) {
                NetworkInterface intf = enumeration.nextElement();
                if (intf.isLoopback() || intf.isVirtual()) {
                    continue; // Skip loopback and virtual interfaces
                }
                Enumeration<InetAddress> inets = intf.getInetAddresses();
                while (inets.hasMoreElements()) {
                    InetAddress addr = inets.nextElement();
                    if (addr.isLoopbackAddress() || !addr.isSiteLocalAddress() || addr.isAnyLocalAddress()) {
                        continue; // Skip loopback and invalid addresses
                    }
                    list.add(addr.getHostAddress());
                }
            }
        } catch (SocketException e) {
            log.warn("", e);
        }
        return list;
    }

    /**
     * Simplifies an IPv6 address by replacing consecutive zero segments with "::".
     * <p>
     * For example: "2001:0db8:0000:0000:0000:8a2e:0370:7334" becomes "2001:0db8::8a2e:0370:7334".
     * </p>
     *
     * @param ip The IP address string (can be IPv4 or IPv6).
     * @return The simplified IP string, or original if it doesn't need simplification.
     */
    public static String fullySimplifyIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }

        String[] segments = ip.split("\\.");
        if (segments.length == 4) {
            return ip; // Already IPv4, no need to simplify
        }

        segments = ip.split(":");

        int longestZeroStart = -1;
        int longestZeroLength = 0;
        int currentZeroStart = -1;
        int currentZeroLength = 0;

        for (int i = 0; i < segments.length; i++) {
            if ("0".equals(segments[i])) {
                if (currentZeroStart == -1) {
                    currentZeroStart = i;
                }
                currentZeroLength++;
            } else {
                if (currentZeroLength > longestZeroLength) {
                    longestZeroStart = currentZeroStart;
                    longestZeroLength = currentZeroLength;
                }
                currentZeroStart = -1;
                currentZeroLength = 0;
            }
        }

        if (currentZeroLength > longestZeroLength) {
            longestZeroStart = currentZeroStart;
            longestZeroLength = currentZeroLength;
        }

        if (longestZeroStart == -1) {
            return ip; // No consecutive zeros found
        }

        return getString(segments, longestZeroStart, longestZeroLength);
    }

    /**
     * Builds a simplified IPv6 string by collapsing zero segments.
     *
     * @param segments          The array of IPv6 segments split by colon (:).
     * @param longestZeroStart  Start index of the longest sequence of zero segments.
     * @param longestZeroLength Length of the longest zero segment sequence.
     * @return A simplified IPv6 address string.
     */
    private static String getString(String[] segments, int longestZeroStart, int longestZeroLength) {
        StringBuilder fullySimplified = new StringBuilder();
        boolean skipNextZeroSegments = false;
        int zeroEndIndex = longestZeroStart + longestZeroLength;

        for (int i = 0; i < segments.length; i++) {
            if (i == longestZeroStart) {
                fullySimplified.append("::");
                skipNextZeroSegments = true;
            } else {
                if (skipNextZeroSegments && i < zeroEndIndex) {
                    continue; // Skip the zero segments after ::
                }

                fullySimplified.append(segments[i]);
                if (i < segments.length - 1) {
                    fullySimplified.append(":");
                }
            }
        }

        return fullySimplified.toString();
    }
}
