package com.haoran.music.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;






@Data
@NoArgsConstructor
@AllArgsConstructor
public class Node3DiskUsage {
    private boolean available;
    private long totalBytes;
    private long usedBytes;
    private long availableBytes;
    private Integer usedPercent;
    private String errorCategory;
}
