package io.github.hiant.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.*;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

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

    // Regular expression pattern for validating IPv4 addresses
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    // Regular expression patterns for validating IPv6 addresses
    private static final Pattern IPV6_STD_PATTERN = Pattern.compile(
            "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile(
            "^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private IpUtils() {
    }

    /**
     * Retrieves the remote address of the client.
     * <p>
     * This method is used to determine the actual IP address of the client that initiated the request,
     * especially in cases where the request passes through multiple proxies. It can obtain the real
     * client IP address even behind multiple proxy layers.
     *
     * @return The remote address of the client, or an empty string if it cannot be obtained.
     */
    public static String getRemoteAddr() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        return getRemoteAddr(request);
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
        if (request == null) {
            return null;
        }
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
     * @return The hostname of the local machine.
     * @throws IllegalStateException if the hostname cannot be determined and no default is provided.
     */
    public static String hostname() {
        return hostname(null);
    }


    /**
     * Gets the hostname of the local machine.
     *
     * <p>If the hostname cannot be determined due to a {@link UnknownHostException},
     * it falls back to the provided {@code defaultHostName}, or to {@code "localhost"}
     * if no default is given. A warning log is generated when fallback occurs.
     *
     * @param defaultHostName A default hostname to use in case of failure, can be null.
     * @return The hostname of the local machine, or the default/fallback value.
     */
    public static String hostname(String defaultHostName) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostName();
        } catch (UnknownHostException e) {
            if (defaultHostName != null) {
                return defaultHostName;
            }
            log.warn("Failed to get hostname, falling back to 'localhost'", e);
            return "localhost";
        }
    }


    /**
     * Returns the first valid IPv4 address of the local machine.
     * If none is found, returns loopback address "127.0.0.1".
     *
     * @return An IPv4 address string.
     */
    public static String localIPv4() {
        List<String> allIp = localIp();
        return allIp.isEmpty() ? "127.0.0.1" : allIp.get(0);
    }

    /**
     * Retrieves all site-local, non-loopback IPv4/IPv6 addresses of the machine.
     *
     * @return A list of IP address strings.
     */
    public static List<String> localIp() {
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

    /**
     * Checks whether the given string represents a valid IPv4 address.
     * <p>
     * This method first validates the format using a regular expression. If the format is invalid,
     * it attempts to resolve the hostname via DNS and checks if any of the returned addresses are IPv4.
     *
     * @param input The string to check.
     * @return true if the input is a valid IPv4 address, false otherwise.
     */
    public static boolean isIPv4(String input) {
        if (isValidIPv4Format(input)) {
            return true;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(input);
            for (InetAddress address : addresses) {
                if (address instanceof Inet4Address) {
                    return true;
                }
            }
        } catch (UnknownHostException ignored) {
        }

        return false;
    }

    /**
     * Checks whether the given string represents a valid IPv6 address.
     * <p>
     * This method first validates the format using regular expressions for standard and compressed IPv6 formats.
     * If the format is not recognized, it tries to resolve the input as a hostname and checks if any of the returned addresses are IPv6.
     *
     * @param input The string to check.
     * @return true if the input is a valid IPv6 address, false otherwise.
     */
    public static boolean isIPv6(String input) {
        if (isValidIPv6Format(input)) {
            return true;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(input);
            for (InetAddress address : addresses) {
                if (address instanceof Inet6Address) {
                    return true;
                }
            }
        } catch (UnknownHostException ignored) {
        }

        return false;
    }

    /**
     * Validates the input string against the IPv4 address pattern.
     * <p>
     * This method only performs format validation and does not attempt DNS resolution.
     *
     * @param input The string to validate.
     * @return true if the input matches the IPv4 address pattern, false otherwise.
     */
    private static boolean isValidIPv4Format(String input) {
        return IPV4_PATTERN.matcher(input).matches();
    }

    /**
     * Validates the input string against known IPv6 address patterns.
     * <p>
     * Supports standard full notation, compressed notation, and IPv4-mapped IPv6 addresses.
     * This method does not perform DNS resolution.
     *
     * @param input The string to validate.
     * @return true if the input matches one of the IPv6 address patterns, false otherwise.
     */
    private static boolean isValidIPv6Format(String input) {
        if (IPV6_STD_PATTERN.matcher(input).matches()) {
            return true;
        }

        if (IPV6_HEX_COMPRESSED_PATTERN.matcher(input).matches()) {
            int colonCount = 0;
            for (char c : input.toCharArray()) {
                if (c == ':') {
                    colonCount++;
                }
            }
            return colonCount <= 7;
        }

        if (input.startsWith("::ffff:") || input.startsWith("0:0:0:0:0:ffff:")) {
            String ipv4Part = input.substring(input.lastIndexOf(':') + 1);
            return isValidIPv4Format(ipv4Part);
        }

        return false;
    }
}
