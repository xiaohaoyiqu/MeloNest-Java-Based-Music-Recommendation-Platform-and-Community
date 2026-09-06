package com.haoran.music.common.enums;

import lombok.Getter;

   
                      
                      
   
@Getter
public enum ContentType {

    SONG("song", "歌曲"),
    LYRIC("lyric", "歌词"),
    ARTIST("artist", "歌手"),
    ALBUM("album", "专辑"),
    PLAYLIST("playlist", "歌单");

    private final String code;
    private final String desc;

    ContentType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ContentType fromCode(String code) {
        for (ContentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
