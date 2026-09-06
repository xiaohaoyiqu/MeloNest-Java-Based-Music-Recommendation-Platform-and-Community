package com.haoran.music.vo.attachment;

import lombok.AllArgsConstructor;
import lombok.Data;






@Data
@AllArgsConstructor
public class PrivateAttachmentGrantVO {
    private Long assetId;
    private String grant;
    private String contentUrl;
}
