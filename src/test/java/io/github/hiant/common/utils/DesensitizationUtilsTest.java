package io.github.hiant.common.utils;

import org.junit.After;
import org.junit.Test;

import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Comprehensive test cases for DesensitizationUtils.
 * Covers: phone/bank card desensitization, general desensitization,
 * hash algorithms, edge cases, security constraints, and concurrency.
 */
public class DesensitizationUtilsTest {

    @After
    public void tearDown() {
        // Prevent system-property leakage across tests (JUnit does not guarantee test order).
        System.clearProperty("desensitize.maskRatio.default");
        System.clearProperty("desensitize.maskRatio.mobile_phone");
        System.clearProperty("desensitize.maskRatio.MOBILE_PHONE");
    }

    // ================================================================================
    // Phone Number Desensitization Tests
    // ================================================================================

    @Test
    public void testPhone_validPhone_shouldMaskMiddleDigits() {
        String result = DesensitizationUtils.phone("13812345678");
        assertEquals("138****5678", result);
    }

    @Test
    public void testPhone_differentPrefixes_shouldMaskCorrectly() {
        // Test various valid phone prefixes
        assertEquals("139****5656", DesensitizationUtils.phone("13912345656"));
        assertEquals("150****1199", DesensitizationUtils.phone("15000001199"));
        assertEquals("188****0000", DesensitizationUtils.phone("18888880000"));
        assertEquals("199****1111", DesensitizationUtils.phone("19999991111"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPhone_nullPhone_shouldThrowException() {
        DesensitizationUtils.phone(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPhone_emptyPhone_shouldThrowException() {
        DesensitizationUtils.phone("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPhone_invalidLength_shouldThrowException() {
        DesensitizationUtils.phone("1381234567"); // 10 digits
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPhone_invalidPrefix_shouldThrowException() {
        DesensitizationUtils.phone("22812345678"); // Not starting with 1
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPhone_containsLetters_shouldThrowException() {
        DesensitizationUtils.phone("138abc45678");
    }

    // ================================================================================
    // Phone Number with Hash Tests
    // ================================================================================

    @Test
    public void testPhoneWithHash_defaultMD5_shouldAppendHash() {
        String result = DesensitizationUtils.phoneWithHash("13812345678");
        assertTrue(result.startsWith("138****5678("));
        assertTrue(result.endsWith(")"));
        // MD5 produces 32 hex characters
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(32, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }

    @Test
    public void testPhoneWithHash_sha256_shouldAppendLongerHash() {
        String result = DesensitizationUtils.phoneWithHash("13812345678",
                DesensitizationUtils.HashAlgorithm.SHA_256);
        assertTrue(result.startsWith("138****5678("));
        // SHA-256 produces 64 hex characters
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(64, hash.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPhoneWithHash_crc32_shouldThrowSecurityException() {
        DesensitizationUtils.phoneWithHash("13812345678",
                DesensitizationUtils.HashAlgorithm.CRC32);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPhoneWithHash_crc16_shouldThrowSecurityException() {
        DesensitizationUtils.phoneWithHash("13812345678",
                DesensitizationUtils.HashAlgorithm.CRC16);
    }

    @Test
    public void testPhoneWithHash_nullAlgorithm_shouldDefaultToMD5() {
        String result = DesensitizationUtils.phoneWithHash("13812345678", null);
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(32, hash.length()); // MD5 = 32 hex chars
    }

    // ================================================================================
    // Bank Card Desensitization Tests
    // ================================================================================

    @Test
    public void testBankCard_16Digits_shouldMaskMiddle() {
        String result = DesensitizationUtils.bankCard("6222021234567890");
        assertEquals("622202****7890", result);
    }

    @Test
    public void testBankCard_19Digits_shouldMaskMiddle() {
        String result = DesensitizationUtils.bankCard("6222021234567890123");
        assertEquals("622202****0123", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBankCard_nullBankCard_shouldThrowException() {
        DesensitizationUtils.bankCard(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBankCard_tooShort_shouldThrowException() {
        DesensitizationUtils.bankCard("123456789012345"); // 15 digits
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBankCard_tooLong_shouldThrowException() {
        DesensitizationUtils.bankCard("12345678901234567890"); // 20 digits
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBankCard_containsLetters_shouldThrowException() {
        DesensitizationUtils.bankCard("622202123456789A");
    }

    // ================================================================================
    // Bank Card with Hash Tests
    // ================================================================================

    @Test
    public void testBankCardWithHash_defaultMD5_shouldAppendHash() {
        String result = DesensitizationUtils.bankCardWithHash("6222021234567890");
        assertTrue(result.startsWith("622202****7890("));
        assertTrue(result.endsWith(")"));
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(32, hash.length());
    }

    @Test
    public void testBankCardWithHash_sha256_shouldWork() {
        String result = DesensitizationUtils.bankCardWithHash("6222021234567890",
                DesensitizationUtils.HashAlgorithm.SHA_256);
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(64, hash.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBankCardWithHash_crc32_shouldThrowSecurityException() {
        DesensitizationUtils.bankCardWithHash("6222021234567890",
                DesensitizationUtils.HashAlgorithm.CRC32);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBankCardWithHash_crc16_shouldThrowSecurityException() {
        DesensitizationUtils.bankCardWithHash("6222021234567890",
                DesensitizationUtils.HashAlgorithm.CRC16);
    }

    // ================================================================================
    // General Desensitization Tests
    // ================================================================================

    @Test
    public void testDesensitize_defaultConfig_shouldUseDefaultValues() {
        // Default: keepPrefix=3, keepSuffix=2, placeholder="****"
        // Need content long enough: 13 chars, keep 3+2=5, mask=8 (61.5% > 60%)
        String result = DesensitizationUtils.desensitize("1234567890ABC");
        assertEquals("123****BC", result);
    }

    @Test
    public void testDesensitize_customPrefixSuffix_shouldWork() {
        String result = DesensitizationUtils.desensitize("ABCDEFGHIJ", 2, 2);
        assertEquals("AB****IJ", result);
    }

    @Test
    public void testDesensitize_customPlaceholder_shouldWork() {
        String result = DesensitizationUtils.desensitize("ABCDEFGHIJ", 2, 2, "***");
        assertEquals("AB***IJ", result);
    }

    @Test
    public void testDesensitize_emptyPlaceholder_shouldUseDefault() {
        String result = DesensitizationUtils.desensitize("ABCDEFGHIJ", 2, 2, "");
        assertEquals("AB****IJ", result);
    }

    @Test
    public void testDesensitize_nullPlaceholder_shouldUseDefault() {
        String result = DesensitizationUtils.desensitize("ABCDEFGHIJ", 2, 2, null);
        assertEquals("AB****IJ", result);
    }

    @Test(expected = NullPointerException.class)
    public void testDesensitize_nullContent_shouldThrowException() {
        DesensitizationUtils.desensitize(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDesensitize_negativePrefix_shouldThrowException() {
        DesensitizationUtils.desensitize("ABCDEFGHIJ", -1, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDesensitize_negativeSuffix_shouldThrowException() {
        DesensitizationUtils.desensitize("ABCDEFGHIJ", 2, -1);
    }

    @Test
    public void testDesensitize_retentionExceedsLength_shouldReturnOriginal() {
        String result = DesensitizationUtils.desensitize("ABC", 2, 2);
        assertEquals("ABC", result);
    }

    @Test
    public void testDesensitize_retentionEqualsLength_shouldReturnOriginal() {
        String result = DesensitizationUtils.desensitize("ABCD", 2, 2);
        assertEquals("ABCD", result);
    }

    // ================================================================================
    // Type-Name Convenience API Tests
    // ================================================================================

    @Test
    public void testDesensitize_typeNameIgnoreCaseAndUnderscore_shouldWork() {
        String result = DesensitizationUtils.desensitize("mobile_phone", "13812345678");
        assertEquals("138****5678", result);
    }

    @Test
    public void testDesensitize_typeAliasMsisdn_shouldWork() {
        String result = DesensitizationUtils.desensitize("msisdn", "13812345678");
        assertEquals("138****5678", result);
    }

    @Test
    public void testDesensitize_typeNameCamelCase_shouldWork() {
        String result = DesensitizationUtils.desensitize("MobilePhone", "13812345678");
        assertEquals("138****5678", result);
    }

    @Test
    public void testDesensitize_typeNameDashAndTrim_shouldWork() {
        String result = DesensitizationUtils.desensitize(" bank-card ", "6222021234567890");
        assertEquals("622202****7890", result);
    }

    @Test
    public void testDesensitizeWithHash_typeNameUnderscore_shouldWork() {
        String result = DesensitizationUtils.desensitizeWithHash("id_card", "110101199001011234", 6, 4);
        assertTrue(result.startsWith("110101****1234("));
        assertTrue(result.endsWith(")"));
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(32, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDesensitize_unknownType_shouldThrowException() {
        DesensitizationUtils.desensitize("unknown_type", "any");
    }

    // ================================================================================
    // Mask-Ratio Policy Tests
    // ================================================================================

    @Test
    public void testDesensitize_defaultMaskRatio_shouldNotValidate_phoneConventionShouldPass() {
        // Default policy: do NOT validate mask ratio unless desensitize.maskRatio.default is configured.
        String result = DesensitizationUtils.desensitize("13812345678", 3, 4);
        assertEquals("138****5678", result);
    }

    @Test
    public void testDesensitize_defaultMaskRatio_shouldNotValidate_bankCardConventionShouldPass() {
        // Default policy: do NOT validate mask ratio unless desensitize.maskRatio.default is configured.
        String result = DesensitizationUtils.desensitize("6222021234567890", 6, 4);
        assertEquals("622202****7890", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDesensitize_configuredDefaultMaskRatio_shouldValidateAndFail_phoneConvention() {
        // Configure a strict ratio; CN mobile 3+4 masks only 4/11 (< 60%), should fail.
        System.setProperty("desensitize.maskRatio.default", "0.6");
        DesensitizationUtils.desensitize("13812345678", 3, 4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDesensitize_configuredDefaultMaskRatio_shouldValidateAndFail_insufficientMaskArea() {
        // 10 characters, keep 3+3=6, mask=4 (40% < 60%).
        System.setProperty("desensitize.maskRatio.default", "0.6");
        DesensitizationUtils.desensitize("ABCDEFGHIJ", 3, 3);
    }

    @Test
    public void testStrategyDesensitize_mobilePhone_noMaskRatioConfig_shouldNotValidate() {
        // Type-based policy: when desensitize.maskRatio.<type> is NOT configured, do not validate.
        String result = DesensitizationUtils.strategyDesensitize(
                "13812345678",
                DesensitizeType.MOBILE_PHONE,
                OptionalInt.empty(),
                OptionalInt.empty(),
                false
        );
        assertEquals("138****5678", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStrategyDesensitize_mobilePhone_configuredMaskRatio_shouldValidateAndFail() {
        // Configure a strict ratio; CN mobile 3+4 will fail.
        System.setProperty("desensitize.maskRatio.mobile_phone", "0.6");
        DesensitizationUtils.strategyDesensitize(
                "13812345678",
                DesensitizeType.MOBILE_PHONE,
                OptionalInt.empty(),
                OptionalInt.empty(),
                false
        );
    }

    @Test
    public void testDesensitizeType_bankCardStrategy_shouldWork() {
        // Ensure the BANK_CARD preset works end-to-end via strategy API.
        String result = DesensitizationUtils.strategyDesensitize(
                "6222021234567890",
                DesensitizeType.BANK_CARD,
                OptionalInt.empty(),
                OptionalInt.empty(),
                false
        );
        assertEquals("622202****7890", result);
    }

    @Test
    public void testStrategyDesensitize_mobilePhoneWithHash_shouldWorkWithoutDefaultMaskRatio() {
        // strategyDesensitize(withHash) masks via preset and then appends hash; default mask-ratio validation is disabled.
        String result = DesensitizationUtils.strategyDesensitize(
                "13812345678",
                DesensitizeType.MOBILE_PHONE,
                OptionalInt.empty(),
                OptionalInt.empty(),
                true
        );
        assertTrue(result.startsWith("138****5678("));
        assertTrue(result.endsWith(")"));
    }

    @Test
    public void testDesensitize_configuredDefaultMaskRatio_shouldValidateAndPass_atBoundary() {
        // 10 characters, keep 2+2=4, mask=6 (60% = 60%), should pass.
        System.setProperty("desensitize.maskRatio.default", "0.6");
        String result = DesensitizationUtils.desensitize("ABCDEFGHIJ", 2, 2);
        assertEquals("AB****IJ", result);
    }

    @Test
    public void testDesensitize_configuredDefaultMaskRatio_shouldValidateAndPass_exceeds() {
        // 10 characters, keep 1+1=2, mask=8 (80% > 60%), should pass.
        System.setProperty("desensitize.maskRatio.default", "0.6");
        String result = DesensitizationUtils.desensitize("ABCDEFGHIJ", 1, 1);
        assertEquals("A****J", result);
    }

    // ================================================================================
    // Unicode / Emoji Support Tests
    // ================================================================================

    @Test
    public void testDesensitize_withEmoji_shouldCountCodePointsCorrectly() {
        // Emoji counts as 1 code point, not 2 chars
        // "Hello😀World" = 11 code points
        String input = "Hello😀World";
        assertEquals(11, input.codePointCount(0, input.length()));

        // keep 2 prefix, 2 suffix, mask 7 (63.6% > 60%)
        String result = DesensitizationUtils.desensitize(input, 2, 2);
        assertEquals("He****ld", result);
    }

    @Test
    public void testDesensitize_withChineseCharacters_shouldWork() {
        // "北京欢迎您" = 5 code points
        String input = "北京欢迎您";
        assertEquals(5, input.codePointCount(0, input.length()));

        // keep 1+1=2, mask 3 (60%)
        String result = DesensitizationUtils.desensitize(input, 1, 1);
        assertEquals("北****您", result);
    }

    // ================================================================================
    // Desensitize with Hash Tests
    // ================================================================================

    @Test
    public void testDesensitizeWithHash_md5_shouldWork() {
        String result = DesensitizationUtils.desensitizeWithHash(
                "1234567890", 2, 2, "****", DesensitizationUtils.HashAlgorithm.MD5);
        assertTrue(result.startsWith("12****90("));
        assertTrue(result.endsWith(")"));
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(32, hash.length());
    }

    @Test
    public void testDesensitizeWithHash_sha256_shouldWork() {
        String result = DesensitizationUtils.desensitizeWithHash(
                "1234567890", 2, 2, "****", DesensitizationUtils.HashAlgorithm.SHA_256);
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(64, hash.length());
    }

    @Test
    public void testDesensitizeWithHash_crc32_shouldWork() {
        // CRC32 is allowed for general content (not phone/bank card)
        String result = DesensitizationUtils.desensitizeWithHash(
                "1234567890", 2, 2, "****", DesensitizationUtils.HashAlgorithm.CRC32);
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(8, hash.length()); // CRC32 = 8 hex chars
    }

    @Test
    public void testDesensitizeWithHash_crc16_shouldWork() {
        // CRC16 is allowed for general content
        String result = DesensitizationUtils.desensitizeWithHash(
                "1234567890", 2, 2, "****", DesensitizationUtils.HashAlgorithm.CRC16);
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(4, hash.length()); // CRC16 = 4 hex chars
    }

    @Test
    public void testDesensitizeWithHash_nullAlgorithm_shouldDefaultToMD5() {
        String result = DesensitizationUtils.desensitizeWithHash(
                "1234567890", 2, 2, "****", null);
        String hash = result.substring(result.indexOf('(') + 1, result.indexOf(')'));
        assertEquals(32, hash.length()); // MD5
    }

    // ================================================================================
    // Hash Consistency Tests
    // ================================================================================

    @Test
    public void testHash_sameInput_shouldProduceSameHash() {
        String input = "13812345678";
        String result1 = DesensitizationUtils.phoneWithHash(input);
        String result2 = DesensitizationUtils.phoneWithHash(input);
        assertEquals(result1, result2);
    }

    @Test
    public void testHash_differentInput_shouldProduceDifferentHash() {
        String result1 = DesensitizationUtils.phoneWithHash("13812345678");
        String result2 = DesensitizationUtils.phoneWithHash("13912345678");

        String hash1 = result1.substring(result1.indexOf('(') + 1, result1.indexOf(')'));
        String hash2 = result2.substring(result2.indexOf('(') + 1, result2.indexOf(')'));

        assertNotEquals(hash1, hash2);
    }

    // ================================================================================
    // HashAlgorithm Enum Tests
    // ================================================================================

    @Test
    public void testHashAlgorithm_algorithmNames_shouldBeCorrect() {
        assertEquals("MD5", DesensitizationUtils.HashAlgorithm.MD5.getAlgorithmName());
        assertEquals("SHA-256", DesensitizationUtils.HashAlgorithm.SHA_256.getAlgorithmName());
        assertEquals("CRC32", DesensitizationUtils.HashAlgorithm.CRC32.getAlgorithmName());
        assertEquals("CRC16", DesensitizationUtils.HashAlgorithm.CRC16.getAlgorithmName());
    }

    @Test
    public void testHashAlgorithm_descriptions_shouldNotBeEmpty() {
        for (DesensitizationUtils.HashAlgorithm algorithm : DesensitizationUtils.HashAlgorithm.values()) {
            assertNotNull(algorithm.getDescription());
            assertFalse(algorithm.getDescription().isEmpty());
        }
    }

    // ================================================================================
    // DesensitizeConfig Tests
    // ================================================================================

    @Test
    public void testDesensitizeConfig_defaults_shouldReturnSingleton() {
        DesensitizationUtils.DesensitizeConfig config1 = DesensitizationUtils.DesensitizeConfig.defaults();
        DesensitizationUtils.DesensitizeConfig config2 = DesensitizationUtils.DesensitizeConfig.defaults();
        assertSame(config1, config2);
    }

    @Test
    public void testDesensitizeConfig_defaultValues_shouldBeValid() {
        DesensitizationUtils.DesensitizeConfig config = DesensitizationUtils.DesensitizeConfig.defaults();
        assertNotNull(config.defaultPlaceholder);
        assertFalse(config.defaultPlaceholder.isEmpty());
        assertTrue(config.defaultKeepPrefix >= 0);
        assertTrue(config.defaultKeepSuffix >= 0);
    }

    // ================================================================================
    // Thread Safety / Concurrency Tests
    // ================================================================================

    @Test
    public void testConcurrency_multipleThreads_shouldProduceConsistentResults() throws InterruptedException {
        final int threadCount = 10;
        final int iterationsPerThread = 100;
        final String testPhone = "13812345678";
        final String expectedResult = "138****5678";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        String result = DesensitizationUtils.phone(testPhone);
                        if (!expectedResult.equals(result)) {
                            errorCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errorCount.get());
    }

    @Test
    public void testConcurrency_hashConsistency_shouldBeThreadSafe() throws InterruptedException {
        final int threadCount = 10;
        final int iterationsPerThread = 50;
        final String testPhone = "13812345678";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Get expected hash first
        String expectedResult = DesensitizationUtils.phoneWithHash(testPhone);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        String result = DesensitizationUtils.phoneWithHash(testPhone);
                        if (!expectedResult.equals(result)) {
                            errorCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errorCount.get());
    }

    // ================================================================================
    // ThreadLocal Cache Cleanup Test
    // ================================================================================

    @Test
    public void testCleanThreadLocalCache_shouldNotThrowException() {
        // Should be callable without issues
        DesensitizationUtils.cleanThreadLocalCache();

        // Should still work after cleanup
        String result = DesensitizationUtils.phone("13812345678");
        assertEquals("138****5678", result);
    }

    // ================================================================================
    // Edge Case Tests
    // ================================================================================

    @Test
    public void testDesensitize_singleCharacterContent_shouldReturnPlaceholder() {
        // Single character with 0 retention: mask=1, 1 >= 0.6*1, returns placeholder
        String result = DesensitizationUtils.desensitize("A", 0, 0, "****");
        assertEquals("****", result);
    }

    @Test
    public void testDesensitize_zeroRetention_shortContent_shouldReturnPlaceholder() {
        // "AB" with keep 0+0, mask 2 = 100% > 60%, returns placeholder
        String result = DesensitizationUtils.desensitize("AB", 0, 0);
        assertEquals("****", result);
    }

    @Test
    public void testDesensitize_allDigits_shouldWork() {
        String result = DesensitizationUtils.desensitize("0123456789", 2, 2);
        assertEquals("01****89", result);
    }

    @Test
    public void testDesensitize_specialCharacters_shouldWork() {
        String result = DesensitizationUtils.desensitize("!@#$%^&*()}", 2, 2);
        assertEquals("!@****)}", result);
    }
}
