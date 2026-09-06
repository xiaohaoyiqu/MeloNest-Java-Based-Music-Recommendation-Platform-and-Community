package com.haoran.music.vo.emoji;

import lombok.Data;

import java.io.Serializable;





@Data
public class EmojiItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long packageId;
    private String name;
    private String imageUrl;
    private String shortcut;
}
