



package com.haoran.music.vo.emoji;

import lombok.Data;

import java.io.Serializable;


@Data
public class EmojiPreviewItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String emojiId;
    private String name;
    private String imageUrl;
}
