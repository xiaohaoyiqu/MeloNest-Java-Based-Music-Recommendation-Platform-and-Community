   
                      
                                
   
package com.haoran.music.service;

   
                                                     
   
public class SmsSendResult {

    private final boolean success;
    private final String provider;
    private final String message;

    private SmsSendResult(boolean success, String provider, String message) {
        this.success = success;
        this.provider = provider;
        this.message = message;
    }

    public static SmsSendResult success(String provider, String message) {
        return new SmsSendResult(true, provider, message);
    }

    public static SmsSendResult failure(String provider, String message) {
        return new SmsSendResult(false, provider, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getProvider() {
        return provider;
    }

    public String getMessage() {
        return message;
    }
}