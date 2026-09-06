




package com.haoran.music.common.util;





public class DataMaskingUtil {




    private static final String MASK = "********";








    public static String maskIdCard(String idCardNo) {
        if (ObjectUtils.isEmpty(idCardNo) || idCardNo.length() < 8) {
            return idCardNo;
        }
        int len = idCardNo.length();

        return idCardNo.substring(0, 4) + repeatMask(len - 8) + idCardNo.substring(len - 4);
    }








    public static String maskPhone(String phone) {
        if (ObjectUtils.isEmpty(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }








    public static String maskEmail(String email) {
        if (ObjectUtils.isEmpty(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return email;
        }
        String prefix = parts[0];
        String suffix = parts[1];
        if (prefix.length() <= 2) {
            return "**@" + suffix;
        }
        return prefix.substring(0, 2) + "****@" + suffix;
    }








    public static String maskBankCard(String cardNo) {
        if (ObjectUtils.isEmpty(cardNo) || cardNo.length() < 8) {
            return cardNo;
        }
        int len = cardNo.length();
        return cardNo.substring(0, 4) + repeatMask(len - 8) + cardNo.substring(len - 4);
    }

    private static String repeatMask(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        while (builder.length() < count) {
            builder.append(MASK);
        }
        return builder.substring(0, count);
    }










    public static String maskName(String name) {
        if (ObjectUtils.isEmpty(name)) {
            return name;
        }
        int len = name.length();
        if (len == 1) {
            return "*";
        } else if (len == 2) {
            return name.charAt(0) + "*";
        } else {
            return name.charAt(0) + "**";
        }
    }







    public static boolean isValidIdCard(String idCardNo) {
        if (ObjectUtils.isEmpty(idCardNo) || idCardNo.length() != 18) {
            return false;
        }

        String body = idCardNo.substring(0, 17);
        char last = idCardNo.charAt(17);
        if (!body.matches("\\d{17}")) {
            return false;
        }
        return last == 'X' || Character.isDigit(last);
    }







    public static boolean isValidPhone(String phone) {
        if (ObjectUtils.isEmpty(phone) || phone.length() != 11) {
            return false;
        }
        return phone.matches("^1[3-9]\\d{9}$");
    }







    public static boolean isValidEmail(String email) {
        if (ObjectUtils.isEmpty(email)) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
