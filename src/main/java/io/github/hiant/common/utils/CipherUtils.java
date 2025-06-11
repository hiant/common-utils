package io.github.hiant.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

@Slf4j
public class CipherUtils {

    private CipherUtils() {
    }

    private static final String AES_CBC_PKCS5PADDING = "AES/CBC/PKCS5Padding";

    private static final String AES_ALGORITHM = "AES";

    private static final String RSA_ALGORITHM = "RSA";

    public static String encryptWithAES(String content, String secretKey, String iv) {
        SecretKeySpec key = new SecretKeySpec(getHexString(secretKey), AES_ALGORITHM);
        IvParameterSpec params = new IvParameterSpec(getHexString(iv));

        return encrypt(content, AES_CBC_PKCS5PADDING, key, params);
    }

    public static String decryptWithAES(String content, String secretKey, String iv) {
        SecretKeySpec key = new SecretKeySpec(getHexString(secretKey), AES_ALGORITHM);
        IvParameterSpec params = new IvParameterSpec(getHexString(iv));

        return decrypt(content, AES_CBC_PKCS5PADDING, key, params);
    }

    public static String encryptWithRSA(String content, Key key) {
        return encrypt(RSA_ALGORITHM, content, key, null);
    }

    public static String decryptWithRSA(String content, Key key) {
        return decrypt(RSA_ALGORITHM, content, key, null);
    }

    public static String encrypt(String content, String cipherMode, Key key, AlgorithmParameterSpec params) {
        try {
            Cipher cipher = Cipher.getInstance(cipherMode);
            if (params == null) {
                cipher.init(Cipher.ENCRYPT_MODE, key);
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, key, params);
            }

            byte[] encryptBytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.getEncoder().encode(encryptBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.info("", e);
        }
        return null;
    }

    public static String decrypt(String content, String cipherMode, Key key, AlgorithmParameterSpec params) {
        try {
            Cipher cipher = Cipher.getInstance(cipherMode);
            if (params == null) {
                cipher.init(Cipher.DECRYPT_MODE, key);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, key, params);
            }

            byte[] data = cipher.doFinal(Base64.getDecoder().decode(content));
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.info("", e);
        }
        return null;
    }

    private static byte[] getHexString(String str) {
        if (str == null) {
            str = "";
        }
        StringBuilder sb = new StringBuilder(16);
        sb.append(str);
        while (sb.length() < 16) {
            sb.append("0");
        }
        if (sb.length() > 16) {
            sb.setLength(16);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

}
