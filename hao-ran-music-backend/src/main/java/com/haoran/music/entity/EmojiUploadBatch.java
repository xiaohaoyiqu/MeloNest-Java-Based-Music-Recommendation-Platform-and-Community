



package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@TableName("emoji_upload_batch")
public class EmojiUploadBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long creatorId;
    private Long packageId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String status;
    private Integer fileCount;
    private Long totalBytes;
    private String uploadedPathsJson;
    private String emojiIdsJson;
    private LocalDateTime expiresAt;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
