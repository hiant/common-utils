package io.github.hiant.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class CipherUtils {

    private CipherUtils() {
    }

    private static final String AES_CBC_PKCS5PADDING = "AES/CBC/PKCS5Padding";

    private static final String AES_ALGORITHM = "AES";

    public static String encryptWithAES(String content, String secretKey, String iv) {
        return encrypt(AES_ALGORITHM, AES_CBC_PKCS5PADDING, content, secretKey, iv);
    }

    public static String decryptWithAES(String content, String secretKey, String iv) {
        return decrypt(AES_ALGORITHM, AES_CBC_PKCS5PADDING, content, secretKey, iv);
    }

    public static String encrypt(String algorithm, String cipherMode, String content, String secretKey, String iv) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(getHexString(secretKey), algorithm);
            Cipher cipher = Cipher.getInstance(cipherMode);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(getHexString(iv)));
            byte[] encryptBytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.getEncoder().encode(encryptBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.info("", e);
        }
        return null;
    }

    public static String decrypt(String algorithm, String cipherMode, String content, String secretKey, String iv) {
        try {
            SecretKeySpec key = new SecretKeySpec(getHexString(secretKey), algorithm);
            Cipher cipher = Cipher.getInstance(cipherMode);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(getHexString(iv)));

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
