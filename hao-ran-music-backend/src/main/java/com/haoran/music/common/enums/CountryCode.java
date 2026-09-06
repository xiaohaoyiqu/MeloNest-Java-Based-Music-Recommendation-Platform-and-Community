package com.haoran.music.common.enums;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

   
         
                      
                      
   
@Getter
public enum CountryCode {

       
             
       
    CN("中国", "CN", "+86", "内地"),

       
           
       
    HK("中国香港", "HK", "+852", "港台"),

       
           
       
    TW("中国台湾", "TW", "+886", "港台"),

       
         
       
    US("美国", "US", "+1", "欧美"),

       
         
       
    UK("英国", "GB", "+44", "欧美"),

       
         
       
    FR("法国", "FR", "+33", "欧美"),

       
         
       
    DE("德国", "DE", "+49", "欧美"),

       
          
       
    CA("加拿大", "CA", "+1", "欧美"),

       
          
       
    IT("意大利", "IT", "+39", "欧美"),

       
         
       
    JP("日本", "JP", "+81", "日本"),

       
         
       
    KR("韩国", "KR", "+82", "韩国"),

       
         
       
    TH("泰国", "TH", "+66", "其他"),

       
         
       
    OTHER("其他", "OTHER", "", "其他");

    private final String name;
    private final String code;
    private final String phoneCode;
    private final String region;

    CountryCode(String name, String code, String phoneCode, String region) {
        this.name = name;
        this.code = code;
        this.phoneCode = phoneCode;
        this.region = region;
    }

       
                 
                       
                     
       
    public static CountryCode fromName(String name) {
        if (name == null) {
            return OTHER;
        }
        for (CountryCode country : values()) {
            if (country.name.equals(name)) {
                return country;
            }
        }
        return OTHER;
    }

       
                 
                       
                     
       
    public static CountryCode fromCode(String code) {
        if (code == null) {
            return OTHER;
        }
        for (CountryCode country : values()) {
            if (country.code.equals(code)) {
                return country;
            }
        }
        return OTHER;
    }

       
                 
                     
       
    public static List<String> getAllNames() {
        List<String> names = new ArrayList<>();
        for (CountryCode country : values()) {
            names.add(country.name);
        }
        return names;
    }

       
                   
                                          
                         
       
    public static List<String> getNamesByRegion(String region) {
        List<String> names = new ArrayList<>();
        for (CountryCode country : values()) {
            if (country.region.equals(region)) {
                names.add(country.name);
            }
        }
        return names;
    }

       
                   
                         
       
    public static List<String> getWesternNames() {
        return Arrays.asList("美国", "英国", "法国", "德国", "加拿大", "意大利");
    }

       
                   
                         
       
    public static List<String> getChineseNames() {
        return Arrays.asList("中国");
    }

       
                
                                    
       
    public boolean isWestern() {
        return "欧美".equals(this.region);
    }

       
                
                                           
       
    public boolean isChineseSpeaking() {
        return "内地".equals(this.region) || "港台".equals(this.region);
    }
}
