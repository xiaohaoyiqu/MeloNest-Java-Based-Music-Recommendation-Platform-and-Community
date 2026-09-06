package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

   
                      
                       
   
@Slf4j
@RestController
@RequestMapping("/files")
public class FileController {

    @Value("${music.upload.avatar-path}")
    private String avatarPath;

    @Value("${music.upload.album-cover-path}")
    private String albumCoverPath;

    @Value("${music.upload.playlist-cover-path}")
    private String playlistCoverPath;

    @Value("${local.upload.path}")
    private String localMusicPath;

       
             
      
                          
                   
  
    @ApiLog("获取头像文件")
    @GetMapping("/avatar/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getAvatar(@PathVariable String filename) {
        return getFile(avatarPath, filename);
    }

       
               
      
                          
                   
  
    @ApiLog("获取专辑封面文件")
    @GetMapping("/album-cover/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getAlbumCover(@PathVariable String filename) {
        return getFile(albumCoverPath, filename);
    }

       
               
      
                          
                   
  
    @ApiLog("获取歌单封面文件")
    @GetMapping("/playlist-cover/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getPlaylistCover(@PathVariable String filename) {
        return getFile(playlistCoverPath, filename);
    }

       
               
      
                                              
                   
       
    @ApiLog("获取本地音乐文件")
    @GetMapping("/local-music/**")
    public ResponseEntity<org.springframework.core.io.Resource> getLocalMusic(HttpServletRequest request) {
        try {
                        
            String path = (String) request.getAttribute(
                    org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

                                        
                                                                             
                                                             
            if (path != null) {
                if (path.startsWith("/files/local-music/")) {
                    path = path.substring("/files/local-music/".length());
                } else if (path.startsWith("/local-music/")) {
                    path = path.substring("/local-music/".length());
                }
            }
            if (path == null || path.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            log.debug("event=local_music_file_requested");
            Path filePath = resolveWithinBase(localMusicPath, path);
            if (filePath == null) {
                log.warn("event=managed_file_path_validation_rejected");
                return ResponseEntity.badRequest().build();
            }
            File file = filePath.toFile();

            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new FileSystemResource(file);

                     
            String contentType = "audio/mpeg";
            String filename = file.getName().toLowerCase();
            if (filename.endsWith(".flac")) {
                contentType = "audio/flac";
            } else if (filename.endsWith(".wav")) {
                contentType = "audio/wav";
            } else if (filename.endsWith(".aac")) {
                contentType = "audio/aac";
            } else if (filename.endsWith(".ogg")) {
                contentType = "audio/ogg";
            } else if (filename.endsWith(".m4a")) {
                contentType = "audio/mp4";
            } else if (filename.endsWith(".wma")) {
                contentType = "audio/x-ms-wma";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"")
                    .header("Accept-Ranges", "bytes")
                    .body(resource);

        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("event=local_music_file_read_failed errorType={}", e.getClass().getSimpleName());
            return ResponseEntity.status(500).build();
        }
    }

       
           
      
                           
                          
                   
       
    private ResponseEntity<org.springframework.core.io.Resource> getFile(String basePath, String filename) {
        try {
            if (filename == null || filename.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            Path filePath = resolveWithinBase(basePath, filename);
            if (filePath == null) {
                return ResponseEntity.badRequest().build();
            }
            File file = filePath.toFile();

            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new FileSystemResource(file);

                                    
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("event=managed_file_read_failed errorType={}", e.getClass().getSimpleName());
            return ResponseEntity.status(500).build();
        }
    }

    private Path resolveWithinBase(String configuredBase, String relativePath) throws IOException {
        if (configuredBase == null || configuredBase.trim().isEmpty()) {
            throw new IOException("managed file root is not configured");
        }
        Path base = Paths.get(configuredBase).toAbsolutePath().normalize();
        Path candidate = base.resolve(relativePath).normalize();
        if (!candidate.startsWith(base)) {
            return null;
        }
        if (Files.exists(candidate)) {
            Path realBase = base.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realBase)) {
                return null;
            }
            return realCandidate;
        }
        return candidate;
    }
}
