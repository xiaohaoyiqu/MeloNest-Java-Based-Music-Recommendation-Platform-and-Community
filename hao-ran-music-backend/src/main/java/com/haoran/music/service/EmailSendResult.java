



package com.haoran.music.service;




public class EmailSendResult {

    private final boolean success;
    private final String provider;
    private final String message;

    private EmailSendResult(boolean success, String provider, String message) {
        this.success = success;
        this.provider = provider;
        this.message = message;
    }

    public static EmailSendResult success(String provider, String message) {
        return new EmailSendResult(true, provider, message);
    }

    public static EmailSendResult failure(String provider, String message) {
        return new EmailSendResult(false, provider, message);
    }

    public boolean isSuccess() { return success; }
    public String getProvider() { return provider; }
    public String getMessage() { return message; }
}