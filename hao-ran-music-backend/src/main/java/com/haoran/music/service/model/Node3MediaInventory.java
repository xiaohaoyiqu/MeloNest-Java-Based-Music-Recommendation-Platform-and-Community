package com.haoran.music.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;






@Data
@NoArgsConstructor
@AllArgsConstructor
public class Node3MediaInventory {
    private boolean available;
    private boolean complete;
    private String errorCategory;
    private List<FileEntry> files = new ArrayList<>();





    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileEntry {
        private String path;
        private long size;
    }
}
