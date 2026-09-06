   
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
                                                                    
   
@Data
@TableName("virus_scan_record")
public class VirusScanRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String clientIp;

    private String businessType;

    private String scanTarget;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String result;

    private String threatName;

    private String scannerHost;

    private Integer scannerPort;

    private Integer quarantined;

    private String quarantinePath;

    private String errorMessage;

    private Long durationMs;

    private LocalDateTime createTime;
}
