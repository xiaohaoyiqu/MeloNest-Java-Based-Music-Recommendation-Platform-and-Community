package com.haoran.music.vo.emoji;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

   
                      
                       
   
@Data
@EqualsAndHashCode(callSuper = true)
public class EmojiPackageDetailVO extends EmojiPackageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<EmojiItemVO> items;
    private SimpleUserVO creator;
    private String createTime;

    @Data
    public static class SimpleUserVO implements Serializable {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
