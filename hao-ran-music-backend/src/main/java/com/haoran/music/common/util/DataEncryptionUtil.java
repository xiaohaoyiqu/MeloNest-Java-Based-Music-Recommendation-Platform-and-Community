




package com.haoran.music.common.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;






public class DataEncryptionUtil {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;


    private static final String DEFAULT_AES_KEY = "your-256-bit-aes-key-here-change-in-production";








    public static String encryptAES(String plainText, String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("AES密钥必须是32字节（256位）");
            }


            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));


            byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }




    public static String encryptAES(String plainText) {
        return encryptAES(plainText, getDefaultAESKey());
    }








    public static String decryptAES(String cipherText, String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            byte[] combined = Base64.getDecoder().decode(cipherText);

            if (combined.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("密文格式错误");
            }


            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plainText = cipher.doFinal(encrypted);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }




    public static String decryptAES(String cipherText) {
        return decryptAES(cipherText, getDefaultAESKey());
    }








    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }








    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 15) {
            return idCard;
        }
        int length = idCard.length();
        return idCard.substring(0, 6) + "********" + idCard.substring(length - 4);
    }








    public static String maskRealName(String realName) {
        if (realName == null || realName.isEmpty()) {
            return realName;
        }
        if (realName.length() == 1) {
            return realName + "*";
        }
        return realName.charAt(0) + "*";
    }








    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        String prefix = email.substring(0, atIndex);
        String suffix = email.substring(atIndex);

        if (prefix.length() <= 2) {
            return "**" + suffix;
        }
        return prefix.substring(0, 2) + "****" + suffix;
    }








    public static String maskBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) {
            return bankCard;
        }
        return bankCard.substring(0, 4) + "************" + bankCard.substring(bankCard.length() - 4);
    }






    public static String generateAESKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE);
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("生成AES密钥失败", e);
        }
    }




    private static String getDefaultAESKey() {

        String key = "ThisIsA32ByteLongKeyForAES256Enc!!";
        return Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }






















}
