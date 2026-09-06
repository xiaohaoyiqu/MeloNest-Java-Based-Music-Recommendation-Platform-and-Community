   
                      
   

package com.haoran.music.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class EmojiUploadBatchResult {
    private Long batchId;
    private int added;
    private List<Long> emojiIds;
}
