package com.haoran.music.vo.attachment;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;






@Data
@AllArgsConstructor
public class PrivateAttachmentDownload {
    private Resource resource;
    private String contentType;
    private String originalName;
    private Long fileSize;
}
