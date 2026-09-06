package com.haoran.music.vo.emoji;

import com.haoran.music.entity.Emoji;
import lombok.Data;

import java.io.Serializable;





@Data
public class EmojiDisplayVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String emojiId;
    private String emojiName;
    private String imageUrl;

    public static EmojiDisplayVO fromEntity(Emoji emoji) {
        EmojiDisplayVO result = new EmojiDisplayVO();
        result.setEmojiId(emoji.getCode());
        result.setEmojiName(emoji.getName());
        result.setImageUrl(emoji.getImageUrl());
        return result;
    }
}
