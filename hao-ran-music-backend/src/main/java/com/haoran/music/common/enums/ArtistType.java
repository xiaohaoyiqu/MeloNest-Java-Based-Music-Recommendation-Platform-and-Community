package com.haoran.music.common.enums;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;





@Getter
public enum ArtistType {




    MALE_SINGER("男歌手", "male_singer"),




    FEMALE_SINGER("女歌手", "female_singer"),




    BAND("乐队", "band"),




    COMPOSER("作曲家", "composer"),




    HOST("主持人", "host"),




    DJ("DJ", "dj"),




    PRODUCER("制作人", "producer"),




    OTHER("其他", "other");

    private final String name;
    private final String code;

    ArtistType(String name, String code) {
        this.name = name;
        this.code = code;
    }






    public static ArtistType fromCode(String code) {
        if (code == null) {
            return OTHER;
        }
        for (ArtistType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return OTHER;
    }





    public static List<String> getAllCodes() {
        List<String> codes = new ArrayList<>();
        for (ArtistType type : values()) {
            codes.add(type.code);
        }
        return codes;
    }




    public static List<String> getMaleSingerTypes() {
        return Arrays.asList(MALE_SINGER.code, BAND.code, COMPOSER.code);
    }




    public static List<String> getFemaleSingerTypes() {
        return Arrays.asList(FEMALE_SINGER.code, BAND.code);
    }




    public static List<String> getInternationalAreaCodes() {
        return Arrays.asList("us", "uk", "fr", "de", "ca", "it", "jp", "kr");
    }




    public static List<String> getAsianAreaCodes() {
        return Arrays.asList("jp", "kr", "th", "sg", "my", "id", "ph", "vn", "other");
    }
}
