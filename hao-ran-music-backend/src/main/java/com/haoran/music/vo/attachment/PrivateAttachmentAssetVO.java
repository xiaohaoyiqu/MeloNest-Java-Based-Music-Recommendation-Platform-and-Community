package com.haoran.music.vo.attachment;

import lombok.Data;






@Data
public class PrivateAttachmentAssetVO {
    private Long assetId;
    private String purpose;
    private String contentType;
    private Long fileSize;
    private String scanStatus;
}
