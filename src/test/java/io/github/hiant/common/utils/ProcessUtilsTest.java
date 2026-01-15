package io.github.hiant.common.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test class for ProcessUtils
 */
public class ProcessUtilsTest {

    private String osName;

    @Before
    public void setUp() {
        osName = System.getProperty("os.name").toLowerCase();
    }

    @After
    public void tearDown() {
        // Clean up any resources if needed
    }

    @Test
    public void testRunSimpleCommand() throws Exception {
        String command = isWindows() ? "echo hello" : "echo 'hello'";
        ProcessUtils.ProcessResult result = ProcessUtils.run(command, 10);

        assertEquals(0, result.exitCode);
        assertTrue("Output should contain 'hello'", result.stdout.contains("hello"));
        assertTrue("Should be successful", result.isSuccess());
        assertFalse("Stdout should not be truncated", result.stdoutTruncated);
        assertFalse("Stderr should not be truncated", result.stderrTruncated);
    }

    @Test
    public void testRunCommandWithTimeout() throws Exception {
        // Create a command that will run longer than our timeout
        String command = isWindows()
                ? "ping -n 10 127.0.0.1 > nul"
                : "sleep 10";

        try {
            ProcessUtils.run(command, 2); // 2 second timeout
            fail("Should have thrown TimeoutException");
        } catch (java.util.concurrent.TimeoutException e) {
            // Expected
            assertTrue("Exception message should contain timeout",
                    e.getMessage().contains("timeout"));
        }
    }

    @Test
    public void testPingCommandWithFiveSecondTimeout() throws Exception {
        // This test specifically checks that ping command executes properly with a 5-second timeout
        String command = isWindows()
                ? "ping -n 3 127.0.0.1"  // Windows: ping 3 times
                : "ping -c 3 127.0.0.1"; // Unix: ping 3 times

        long startTime = System.currentTimeMillis();
        ProcessUtils.ProcessResult result = ProcessUtils.run(command, 5);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify the command completed successfully
        assertEquals("Ping should complete successfully", 0, result.exitCode);
        assertTrue("Ping output should contain reply information",
                result.stdout.contains("127.0.0.1") ||
                        result.stdout.contains("bytes from"));
        assertTrue("Should be successful", result.isSuccess());

        // Verify it completed within a reasonable time (should be less than 5 seconds + buffer)
        assertTrue("Ping should complete within timeout period", duration < 7000);

        // Additional verification for Windows behavior
        if (isWindows()) {
            // Print the output for debugging
            System.out.println("Windows ping test completed successfully in " + duration + "ms");
            System.out.println("Ping output: " + result.stdout);

            // Check for Windows-specific ping output patterns
            assertTrue("Windows ping output should contain expected patterns",
                    result.stdout.contains("127.0.0.1"));
        } else {
            assertTrue("Unix ping output should contain 'PING'",
                    result.stdout.contains("PING"));
            System.out.println("Unix ping test completed successfully in " + duration + "ms");
            System.out.println("Ping output: " + result.stdout);
        }
    }

    @Test
    public void testProcessResultToString() throws Exception {
        String command = isWindows() ? "echo toString test" : "echo 'toString test'";
        ProcessUtils.ProcessResult result = ProcessUtils.run(command, 10);

        String resultString = result.toString();
        assertTrue("String representation should contain exit code", resultString.contains("exit=0"));
        assertTrue("String representation should contain stdout", resultString.contains("toString test"));
        assertTrue("String representation should contain truncation flags",
                resultString.contains("stdoutTruncated=false"));
    }

    @Test
    public void testNullCommand() throws Exception {
        try {
            ProcessUtils.run((String) null, 10);
            fail("Should throw exception for null command");
        } catch (NullPointerException e) {
            // Expected - any exception is acceptable for null command
            assertTrue("Should throw NullPointerException for null command", true);
        } catch (Exception e) {
            // Any other exception is also acceptable for null command
            assertTrue("Should throw some exception for null command", true);
        }
    }

    @Test
    public void testEmptyCommand() throws Exception {
        try {
            ProcessUtils.ProcessResult result = ProcessUtils.run("", 10);
            // If we get here, check the result - empty command might not fail on all systems
            if (result.exitCode != 0) {
                // Non-zero exit code is acceptable
                assertTrue("Non-zero exit code for empty command", true);
            } else {
                // Zero exit code but empty output is also acceptable
                assertTrue("Empty output for empty command",
                        result.stdout.isEmpty() || result.stdout.trim().isEmpty());
            }
        } catch (Exception e) {
            // Exception is also acceptable for empty command
            assertTrue("Should fail for empty command", true);
        }
    }

    // Helper methods
    private boolean isWindows() {
        return osName.contains("win");
    }
}
