package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;






@Data
@TableName("media_asset")
public class MediaAsset implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STORAGE_NODE_LOCAL = "node1";
    public static final String STORAGE_NODE_MEDIA = "node3";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RECLAIMING = "RECLAIMING";
    public static final String STATUS_RECLAIMED = "RECLAIMED";
    public static final String STATUS_RECLAIM_FAILED = "RECLAIM_FAILED";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";
    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String SCAN_STATUS_CLEAN = "CLEAN";




    @TableId(type = IdType.AUTO)
    private Long id;




    private Long ownerId;




    private Long uploadSessionId;




    private String purpose;




    private String visibility;




    private String originalName;




    private String contentType;




    private String mediaType;




    private String sourceType;




    private Long sourceId;




    private String assetRole;




    private String publicUrl;




    private String storageNode;




    private String storagePath;




    private String fileHash;




    private Long fileSize;




    private String scanStatus;




    private String status;




    private LocalDateTime graceUntil;




    private String lastError;




    private LocalDateTime reclaimedAt;




    private LocalDateTime createTime;




    @TableField("updated_at")
    private LocalDateTime updateTime;
}
