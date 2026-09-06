package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.MusicSquareWork;
import com.haoran.music.common.util.AudioQualityDetector;





public interface MusicSquareWorkService extends IService<MusicSquareWork> {







    Long submitWork(Long userId, MusicSquareWorkDTO dto);








    void reviewWork(Long workId, Long reviewerId, Integer status, String reviewReason);








    IPage<MusicSquareWork> pageWorks(PageQuery pageQuery, Integer workType, Integer status);




    MusicSquareWork getVisibleWorkDetail(Long workId, Long viewerId);







    IPage<MusicSquareWork> getMyWorks(Long userId, PageQuery pageQuery);





    Long getPendingCount();






    void likeWork(Long workId, Long userId);






    void unlikeWork(Long workId, Long userId);






    void incrementViewCount(Long workId, Long userId);






    void deleteWork(Long workId, Long userId);







    void updateWork(Long workId, Long userId, MusicSquareWorkDTO dto);







    AudioQualityDetector.AudioInfo detectAudioInfo(Long userId, String audioUrl);







    VideoInfo detectVideoInfo(Long userId, String videoUrl);




    class VideoInfo {
        private Long fileSize;
        private Integer duration;
        private String quality;                       
        private String format;                 
        private Integer width;
        private Integer height;

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public String getQuality() { return quality; }
        public void setQuality(String quality) { this.quality = quality; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public Integer getWidth() { return width; }
        public void setWidth(Integer width) { this.width = width; }
        public Integer getHeight() { return height; }
        public void setHeight(Integer height) { this.height = height; }
    }




    class MusicSquareWorkDTO {
        private Integer workType;                                          
        private String title;                   
        private String description;             
        private String coverUrl;                
        private String tags;                    
        private String audioUrl;                   
        private String videoUrl;                   
        private String lyricContent;              
        private String lyricFileUrl;                 
        private Integer uploadType;               
        private String fileUrls;                    
        private String zipFileUrl;                  

        public Integer getWorkType() { return workType; }
        public void setWorkType(Integer workType) { this.workType = workType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCoverUrl() { return coverUrl; }
        public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getAudioUrl() { return audioUrl; }
        public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
        public String getVideoUrl() { return videoUrl; }
        public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
        public String getLyricContent() { return lyricContent; }
        public void setLyricContent(String lyricContent) { this.lyricContent = lyricContent; }
        public String getLyricFileUrl() { return lyricFileUrl; }
        public void setLyricFileUrl(String lyricFileUrl) { this.lyricFileUrl = lyricFileUrl; }
        public Integer getUploadType() { return uploadType; }
        public void setUploadType(Integer uploadType) { this.uploadType = uploadType; }
        public String getFileUrls() { return fileUrls; }
        public void setFileUrls(String fileUrls) { this.fileUrls = fileUrls; }
        public String getZipFileUrl() { return zipFileUrl; }
        public void setZipFileUrl(String zipFileUrl) { this.zipFileUrl = zipFileUrl; }
    }
}
