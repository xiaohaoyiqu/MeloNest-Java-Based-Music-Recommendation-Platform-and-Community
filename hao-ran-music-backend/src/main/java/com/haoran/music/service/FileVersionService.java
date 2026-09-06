   
                      
                        
  
      
                    
                 
                  
                    
   

package com.haoran.music.service;

import com.haoran.music.common.util.EnhancedImageCompressUtil;
import com.haoran.music.common.util.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
           
   
@Slf4j
@Service
public class FileVersionService {

       
              
       
    @Value("${music.upload.base-path}")
    private String basePath;

       
                             
       
    @Value("${music.node4.host}")
    private String node4Host;

    @Value("${music.node4.user}")
    private String node4User;

    @Value("${music.node4.path}")
    private String node4Path;

       
             
       
    private static final List<EnhancedImageCompressUtil.ImageSize> IMAGE_VERSIONS =
            Arrays.asList(
            EnhancedImageCompressUtil.ImageSize.THUMBNAIL,
            EnhancedImageCompressUtil.ImageSize.SMALL,
            EnhancedImageCompressUtil.ImageSize.MEDIUM,
            EnhancedImageCompressUtil.ImageSize.LARGE,
            EnhancedImageCompressUtil.ImageSize.ORIGINAL
    );

       
                  
       
    public enum AudioQuality {
        STANDARD("标准音质", 128000, "standard"),
        HIGH("高音质", 320000, "high"),
        LOSSLESS("无损音质", 1411000, "lossless");

        private final String name;
        private final int bitrate;
        private final String code;

        AudioQuality(String name, int bitrate, String code) {
            this.name = name;
            this.bitrate = bitrate;
            this.code = code;
        }

        public String getName() { return name; }
        public int getBitrate() { return bitrate; }
        public String getCode() { return code; }
    }

       
              
       
    public enum VideoQuality {
        SD("标清", 480, "sd"),
        HD("高清", 720, "hd"),
        FULL_HD("超清", 1080, "full_hd"),
        FOUR_K("4K", 2160, "4k");

        private final String name;
        private final int height;
        private final String code;

        VideoQuality(String name, int height, String code) {
            this.name = name;
            this.height = height;
            this.code = code;
        }

        public String getName() { return name; }
        public int getHeight() { return height; }
        public String getCode() { return code; }
    }

       
                
      
                                 
                                                  
                              
                    
       
    public Map<String, String> generateImageVersions(String originalPath, String category, String fileName) {
        if (ObjectUtils.isEmpty(originalPath) || ObjectUtils.isEmpty(fileName)) {
            return new HashMap<>();
        }

        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String outputDir = basePath + category + "/versions/" + baseName;

        try {
            EnhancedImageCompressUtil.MultiVersionResult result =
                    EnhancedImageCompressUtil.generateVersions(
                            originalPath,
                            outputDir,
                            baseName,
                            IMAGE_VERSIONS.toArray(new EnhancedImageCompressUtil.ImageSize[0])
                    );

            log.info("event=image_versions_generated versionCount={}", result.getVersionPaths().size());

            return result.getVersionPaths();

        } catch (Exception e) {
            log.error("event=image_version_generation_failed errorType={}",
                    e.getClass().getSimpleName());
            return new HashMap<>();
        }
    }

       
                  
      
                           
                            
                                                                  
                   
       
    public String getImageVersionPath(String category, String fileName, String version) {
        if (ObjectUtils.isEmpty(fileName) || ObjectUtils.isEmpty(version)) {
            return null;
        }

        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String versionPath = basePath + category + "/versions/" + baseName + "/" + baseName + "_" + version + "." + getFileExtension(fileName);

        File file = new File(versionPath);
        if (file.exists()) {
            return "/" + category + "/versions/" + baseName + "/" + baseName + "_" + version + "." + getFileExtension(fileName);
        }

                       
        return "/" + category + "/" + fileName;
    }

       
                    
      
                              
                            
                   
       
    public String getAudioVersionPath(Long audioId, AudioQuality quality) {
                             
                              
        return "/songs/" + quality.getCode() + "/" + audioId + ".mp3";
    }

       
                     
      
                             
                            
                   
       
    public String getVideoVersionPath(Long videoId, VideoQuality quality) {
        return "/videos/" + quality.getCode() + "/" + videoId + ".mp4";
    }

       
                  
      
                   
       
    public List<Map<String, Object>> getAvailableAudioQualities() {
        List<Map<String, Object>> qualities = new ArrayList<>();

        Map<String, Object> standard = new HashMap<>();
        standard.put("code", AudioQuality.STANDARD.getCode());
        standard.put("name", AudioQuality.STANDARD.getName());
        standard.put("bitrate", AudioQuality.STANDARD.getBitrate());
        qualities.add(standard);

        Map<String, Object> high = new HashMap<>();
        high.put("code", AudioQuality.HIGH.getCode());
        high.put("name", AudioQuality.HIGH.getName());
        high.put("bitrate", AudioQuality.HIGH.getBitrate());
        qualities.add(high);

        Map<String, Object> lossless = new HashMap<>();
        lossless.put("code", AudioQuality.LOSSLESS.getCode());
        lossless.put("name", AudioQuality.LOSSLESS.getName());
        lossless.put("bitrate", AudioQuality.LOSSLESS.getBitrate());
        qualities.add(lossless);

        return qualities;
    }

       
                     
      
                    
       
    public List<Map<String, Object>> getAvailableVideoQualities() {
        List<Map<String, Object>> qualities = new ArrayList<>();

        Map<String, Object> sd = new HashMap<>();
        sd.put("code", VideoQuality.SD.getCode());
        sd.put("name", VideoQuality.SD.getName());
        sd.put("height", VideoQuality.SD.getHeight());
        qualities.add(sd);

        Map<String, Object> hd = new HashMap<>();
        hd.put("code", VideoQuality.HD.getCode());
        hd.put("name", VideoQuality.HD.getName());
        hd.put("height", VideoQuality.HD.getHeight());
        qualities.add(hd);

        Map<String, Object> fullHd = new HashMap<>();
        fullHd.put("code", VideoQuality.FULL_HD.getCode());
        fullHd.put("name", VideoQuality.FULL_HD.getName());
        fullHd.put("height", VideoQuality.FULL_HD.getHeight());
        qualities.add(fullHd);

        return qualities;
    }

       
                  
      
                         
                          
                      
       
    public int cleanupFileVersions(String category, String fileName) {
        if (ObjectUtils.isEmpty(fileName)) {
            return 0;
        }

        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String versionDir = basePath + category + "/versions/" + baseName;

        File dir = new File(versionDir);
        if (!dir.exists()) {
            return 0;
        }

        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.delete()) {
                    count++;
                }
            }
        }

                 
        dir.delete();

        log.info("event=file_versions_cleaned category={} count={}", category, count);

        return count;
    }

       
              
       
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "jpg";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "jpg";
        }
        return fileName.substring(lastDotIndex + 1);
    }

       
                    
       
    public Map<String, String> getNode4Info() {
        Map<String, String> info = new HashMap<>();
        info.put("host", node4Host);
        info.put("user", node4User);
        info.put("path", node4Path);
        info.put("status", "active");
        info.put("note", "暂时指向node2，未来node4将作为专用缓存节点");
        return info;
    }
}
