package com.haoran.music.enums;

import lombok.Getter;

   
                      
                        
   
@Getter
public enum ProductionType {
       
            
       
    DEMO("demo", "Demo版"),

       
          
       
    OFFICIAL("official", "正式版"),

       
          
       
    REMASTERED("remastered", "重制版"),

       
          
       
    DELUXE("deluxe", "豪华版");

    private final String code;
    private final String name;

    ProductionType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ProductionType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return OFFICIAL;
        }
        for (ProductionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return OFFICIAL;
    }

    public static ProductionType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return OFFICIAL;
        }
        for (ProductionType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return OFFICIAL;
    }
}
