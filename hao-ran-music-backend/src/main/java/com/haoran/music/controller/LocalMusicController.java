package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.config.MusicStorageConfig;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.dto.localMusic.LocalMusicAddDTO;
import com.haoran.music.dto.localMusic.LocalMusicMVAddDTO;
import com.haoran.music.dto.localMusic.LocalMusicUpdateDTO;
import com.haoran.music.service.LocalMusicService;
import com.haoran.music.vo.song.LocalMusicVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.File;
import java.util.List;

   
                      
                                                                                                      
   
@Slf4j
@RestController
@RequestMapping("/local-music")
public class LocalMusicController {

    @Resource
    private LocalMusicService localMusicService;

    @Resource
    private MusicStorageConfig musicStorageConfig;

    @Value("${local.upload.path}")
    private String localMusicPath;

                                                     

       
                   
      
                                 
      
                                                                     
                       
                       
                                     
                   
  
    @ApiLog("扫描可用歌曲")
    @GetMapping({"/scan", "/song/scan"})
    public Result<IPage<LocalMusicVO>> scanSongs(
            @RequestParam(defaultValue = "all") String path,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size,
            @RequestAttribute(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("event=local_music_song_scan_requested userId={}", userId);

        IPage<LocalMusicVO> result = localMusicService.scanSongsByPath(
                path, userId, new PageQuery(page, size), ""
        );
        return Result.success(result);
    }

       
                             
      
                         
                              
                       
       
    @ApiLog("添加本地音乐路径")
    @PostMapping({"/add", "/song/add"})
    public Result<LocalMusicVO> addLocalMusic(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody @Valid LocalMusicAddDTO dto) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        String filePath = dto.getFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            return Result.error("文件路径不能为空");
        }

                 
        boolean isNetworkUrl = filePath.startsWith("http://") || filePath.startsWith("https://");
        boolean isAbsolutePath = filePath.contains(":") || filePath.startsWith("/");

                 
        if (isAbsolutePath && !isNetworkUrl) {
            return Result.error("不支持绝对路径。请使用相对路径（如 songs/standard/song.mp3）或网络URL");
        }

                 
        LocalMusicVO result = localMusicService.addLocalMusic(
                userId,
                filePath,
                dto.getName(),
                dto.getArtist(),
                dto.getAlbum(),
                ""
        );
        return Result.success(result);
    }

       
                            
      
                         
                                
                        
  
    @ApiLog("批量添加本地音乐")
    @PostMapping({"/add-by-ids", "/song/add-by-ids"})
    public Result<List<LocalMusicVO>> addSongsByIds(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody BatchAddRequest request) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        if (request.getSongIds() == null || request.getSongIds().isEmpty()) {
            return Result.error("歌曲ID列表不能为空");
        }

        log.info("event=local_music_batch_add_requested userId={} count={}", userId, request.getSongIds().size());

        List<LocalMusicVO> results = localMusicService.addBySongIds(userId, request.getSongIds(), "");
        return Result.success(results);
    }

       
                           
      
                            
                                              
                        
       
    @ApiLog("批量添加本地音乐路径")
    @PostMapping({"/add-batch", "/song/add-batch"})
    public Result<List<LocalMusicVO>> addBatch(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody List<String> filePaths) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        List<LocalMusicVO> results = localMusicService.addBatchLocalMusic(userId, filePaths, "");
        return Result.success(results);
    }

       
               
      
                         
                              
                       
  
    @ApiLog("手动添加本地单曲")
    @PostMapping({"/add-song-manual", "/song/add-manual"})
    public Result<LocalMusicVO> addManualSong(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody @Valid LocalMusicMVAddDTO dto) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        LocalMusicVO result = localMusicService.addManualSong(
                userId, dto.getName(), dto.getArtist(), dto.getUrl(), dto.getCover(), "", dto.getFileSize(), dto.getDuration(), dto.getQuality()
        );
        return Result.success(result);
    }

       
                      
      
                                          
                                          
                            
                            
                            
                           
  
    @ApiLog("搜索本地音乐版本")
    @GetMapping({"/search-by-name", "/song/search-by-name"})
    public Result<IPage<LocalMusicVO>> searchBySongName(
            @RequestParam String songName,
            @RequestParam Long excludeId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestAttribute(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        if (songName == null || songName.trim().isEmpty()) {
            return Result.error("歌曲名称不能为空");
        }

        IPage<LocalMusicVO> result = localMusicService.searchBySongName(
                songName, excludeId, userId, new PageQuery(page, size), ""
        );

        return Result.success(result);
    }

       
                     
      
                               
                               
                                          
                     
       
    @ApiLog("获取本地音乐列表")
    @GetMapping({"/list", "/song/list"})
    public Result<IPage<LocalMusicVO>> getList(
            @RequestAttribute(value = "userId", required = false) Long userId,
            PageQuery pageQuery,
            @RequestParam(required = false) Integer resourceType) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        IPage<LocalMusicVO> result = localMusicService.getUserLocalMusic(userId, pageQuery, "", resourceType);
        return Result.success(result);
    }

       
               
      
                           
                         
                     
       
    @ApiLog("获取本地音乐详情")
    @GetMapping({"/{id}", "/song/{id}"})
    public Result<LocalMusicVO> getDetail(
            @PathVariable("id") Long id,
            @RequestAttribute(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        LocalMusicVO result = localMusicService.getLocalMusicDetail(id, userId, "");
        return Result.success(result);
    }

       
             
      
                           
                         
                   
       
    @ApiLog("删除本地音乐")
    @DeleteMapping({"/{id}", "/song/{id}"})
    public Result<Boolean> delete(
            @PathVariable("id") Long id,
            @RequestAttribute(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        Boolean result = localMusicService.deleteLocalMusic(id, userId);
        return Result.success(result);
    }

       
               
      
                           
                         
                         
                       
  
    @ApiLog("更新本地音乐信息")
    @PutMapping({"/{id}", "/song/{id}"})
    public Result<LocalMusicVO> update(
            @PathVariable("id") Long id,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody @Valid LocalMusicUpdateDTO dto) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        LocalMusicVO result = localMusicService.updateLocalMusic(
                id, userId, dto.getName(), dto.getArtistName(), dto.getAlbumName(),
                dto.getVersionType(), dto.getVersionName(), ""
        );
        return Result.success(result);
    }

       
               
      
                            
                          
                            
                   
  
    @ApiLog("更新本地音乐歌词")
    @PutMapping({"/{id}/lyric", "/song/{id}/lyric"})
    public Result<Boolean> updateLyric(
            @PathVariable("id") Long id,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody LyricUpdateRequest request) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        if (request == null || request.getLyric() == null || request.getLyric().trim().isEmpty()) {
            return Result.error("歌词内容不能为空");
        }
        if (request.getLyric().trim().length() > 20000) {
            return Result.error("歌词内容不能超过20000个字符");
        }

        Boolean result = localMusicService.updateLocalMusicLyric(id, userId, request.getLyric());
        return Result.success(result);
    }

       
               
      
                             
                         
                   
       
    @ApiLog("批量删除本地音乐")
    @DeleteMapping({"/batch", "/song/batch"})
    public Result<Integer> batchDelete(
            @RequestBody List<Long> ids,
            @RequestAttribute(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        Integer result = localMusicService.batchDeleteLocalMusic(ids, userId);
        return Result.success(result);
    }

       
               
      
                         
                   
       
    @ApiLog("清空本地音乐")
    @DeleteMapping({"/clear", "/song/clear"})
    public Result<Boolean> clear(
            @RequestAttribute(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        Boolean result = localMusicService.clearUserLocalMusic(userId);
        return Result.success(result);
    }

       
             
      
                           
                         
                   
       
    @ApiLog("本地音乐播放")
    @PostMapping({"/{id}/play", "/song/{id}/play"})
    public Result<Boolean> addPlayCount(
            @PathVariable("id") Long id,
            @RequestAttribute(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        Boolean result = localMusicService.incrementPlayCount(userId, id);
        return Result.success(result);
    }

                                                     

       
                   
      
                              
      
                                                          
                         
                         
                                     
                   
  
    @ApiLog("扫描可用MV")
    @GetMapping({"/scan-mv", "/mv/scan"})
    public Result<IPage<LocalMusicVO>> scanMVs(
            @RequestParam(defaultValue = "all") String path,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size,
            @RequestAttribute(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("event=local_music_mv_scan_requested userId={}", userId);

        IPage<LocalMusicVO> result = localMusicService.scanMVsByPath(
                path, userId, new PageQuery(page, size), ""
        );
        return Result.success(result);
    }

       
                           
      
                          
                                 
                        
  
    @ApiLog("批量添加本地MV")
    @PostMapping({"/add-mv-by-ids", "/mv/add-by-ids"})
    public Result<List<LocalMusicVO>> addMVsByIds(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody MVBatchAddRequest request) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        if (request.getMvIds() == null || request.getMvIds().isEmpty()) {
            return Result.error("MV ID列表不能为空");
        }

        log.info("event=local_music_mv_batch_add_requested userId={} count={}", userId, request.getMvIds().size());

        List<LocalMusicVO> results = localMusicService.addMVsByMvIds(userId, request.getMvIds(), "");
        return Result.success(results);
    }

       
               
      
                         
                              
                       
  
    @ApiLog("手动添加本地MV")
    @PostMapping({"/add-mv-manual", "/mv/add-manual"})
    public Result<LocalMusicVO> addManualMV(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody @Valid LocalMusicMVAddDTO dto) {

        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        LocalMusicVO result = localMusicService.addManualMV(
                userId, dto.getName(), dto.getArtist(), dto.getUrl(), dto.getCover(), "", dto.getFileSize(), dto.getDuration(), dto.getQuality()
        );
        return Result.success(result);
    }

                                                     

       
                  
      
                        
  
    @ApiLog("下载本地代理服务")
    @GetMapping({"/download-proxy", "/proxy/download"})
    public ResponseEntity<org.springframework.core.io.Resource> downloadProxy() {
        try {
            List<String> possiblePaths = musicStorageConfig.getLocalProxyPackagePaths();
            if (ObjectUtils.isEmpty(possiblePaths)) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            File zipFile = null;

            for (String path : possiblePaths) {
                File file = new File(path);
                if (file.isFile()) {
                    zipFile = file;
                    log.info("event=local_music_proxy_archive_found source=direct");
                    break;
                } else if (file.isDirectory()) {
                    File[] files = file.listFiles((dir, name) ->
                            name.endsWith(".zip") || name.endsWith(".tar.gz"));
                    if (files != null && files.length > 0) {
                        zipFile = files[0];
                        log.info("event=local_music_proxy_archive_found source=nested");
                        break;
                    }
                }
            }

            if (zipFile == null || !zipFile.exists()) {
                log.warn("event=local_music_proxy_archive_missing");
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource fileResource = new FileSystemResource(zipFile);
            long contentLength = zipFile.length();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", zipFile.getName());
            headers.setContentLength(contentLength);

            log.info("event=local_music_proxy_archive_downloaded sizeBytes={}", contentLength);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileResource);

        } catch (Exception e) {
            log.error("event=local_music_proxy_archive_download_failed errorType={}",
                    e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().build();
        }
    }

                                                    

       
             
       
    public static class LyricUpdateRequest {
        private String lyric;

        public String getLyric() {
            return lyric;
        }

        public void setLyric(String lyric) {
            this.lyric = lyric;
        }
    }

       
                 
       
    public static class BatchAddRequest {
        private List<Long> songIds;

        public List<Long> getSongIds() {
            return songIds;
        }

        public void setSongIds(List<Long> songIds) {
            this.songIds = songIds;
        }
    }

       
                 
       
    public static class MVBatchAddRequest {
        private List<Long> mvIds;

        public List<Long> getMvIds() {
            return mvIds;
        }

        public void setMvIds(List<Long> mvIds) {
            this.mvIds = mvIds;
        }
    }
}
