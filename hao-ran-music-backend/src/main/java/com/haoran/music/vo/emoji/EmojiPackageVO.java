package com.haoran.music.vo.emoji;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;





@Data
public class EmojiPackageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String coverUrl;
    private Long coverEmojiId;
    private String type;                   
    private String typeName;
    private String category;
    private String categoryName;
    private Long price;
    private String purchaseMode;
    private BigDecimal cashPrice;
    private Boolean isFree;
    private Integer downloadCount;
    private Integer itemCount;
    private Integer itemLimit;
    private Integer remainingCount;

    private List<EmojiPreviewItemVO> previewItems;
    private Boolean isPurchased;
    private Boolean isFavorited;
}
