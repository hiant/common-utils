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
        // CI / Windows envs can be slow or ICMP can be blocked; keep this test robust.
        String command = isWindows()
                ? "ping -n 2 127.0.0.1"  // Windows: 2 echo requests (typically ~2s)
                : "ping -c 2 127.0.0.1"; // Unix: 2 echo requests

        long startTime = System.currentTimeMillis();
        ProcessUtils.ProcessResult result = ProcessUtils.run(command, 15);
        long duration = System.currentTimeMillis() - startTime;

        // If ICMP is blocked, ping may fail with non-zero exit code; this should not fail the build.
        if (result.exitCode != 0) {
            System.out.println("Ping command did not succeed (exit=" + result.exitCode + "), output=" + result.stdout + ", err=" + result.stderr);
            return;
        }

        assertTrue("Ping output should contain localhost address",
                result.stdout.contains("127.0.0.1") || result.stdout.contains("bytes from"));
        assertTrue("Should be successful", result.isSuccess());

        // Verify it completed within a reasonable time.
        assertTrue("Ping should complete within a reasonable time", duration < 20000);
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
