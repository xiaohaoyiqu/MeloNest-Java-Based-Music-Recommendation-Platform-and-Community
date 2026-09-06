   
                      
                                      
   

package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.haoran.music.common.config.PostMediaConfig;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.FileSecurityUtil;
import com.haoran.music.common.util.CompressUtil;
import com.haoran.music.entity.MusicPost;
import com.haoran.music.mapper.MusicPostMapper;
import com.haoran.music.service.PostImageService;
import com.haoran.music.service.MusicPostService;
import com.haoran.music.service.VirusScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

   
             
   
@Slf4j
@Service
public class PostImageServiceImpl implements PostImageService {

    @Autowired
    private MusicPostMapper musicPostMapper;

    @Autowired
    private PostMediaConfig postMediaConfig;


    @Autowired
    private MusicUploadConfig musicUploadConfig;

    @Autowired
    private VirusScanService virusScanService;

    @Autowired
    private MusicPostService musicPostService;

    @Override
    public Map<String, String> uploadPostImage(MultipartFile file, Long userId) {
        String baseName = userId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        return uploadImage(file, baseName);
    }

    @Override
    public List<Map<String, String>> uploadPostImages(List<MultipartFile> files, Long userId) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("图片列表不能为空");
        }
        int maxFiles = postMediaConfig.getImageMaxFiles() == null
                ? 9 : postMediaConfig.getImageMaxFiles();
        if (files.size() > maxFiles) {
            throw new IllegalArgumentException("单次最多上传" + maxFiles + "张图片");
        }
        List<Map<String, String>> results = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            String baseName = userId + "_" + System.currentTimeMillis() + "_" + i;
            try {
                Map<String, String> imageInfo = uploadImage(files.get(i), baseName);
                results.add(imageInfo);
            } catch (Exception e) {
                log.error("event=post_image_batch_item_failed index={} errorType={}",
                        i, e.getClass().getSimpleName());
                         
                Map<String, String> errorInfo = new HashMap<>();
                errorInfo.put("error", "图片上传失败，请检查文件后重试");
                results.add(errorInfo);
            }
        }

        return results;
    }

    @Override
    public Map<String, String> uploadPostImageWithId(MultipartFile file, Long postId, Integer index) {
        String baseName = "post_" + postId + "_" + index;
        return uploadImage(file, baseName);
    }

    @Override
    public List<Map<String, Object>> getPostImages(Long postId, Long viewerId) {
        List<Map<String, Object>> imageList = new ArrayList<>();

        if (postId == null) {
            return imageList;
        }
        musicPostService.requirePostReadable(postId, viewerId);

                     
        MusicPost post = musicPostMapper.selectById(postId);
        if (post == null || StrUtil.isBlank(post.getImages())) {
            return imageList;
        }

        try {
                              
            List<Object> images = JSON.parseArray(post.getImages(), Object.class);

            for (int i = 0; i < images.size(); i++) {
                Object stored = images.get(i);
                com.alibaba.fastjson2.JSONObject img = stored instanceof com.alibaba.fastjson2.JSONObject
                        ? (com.alibaba.fastjson2.JSONObject) stored : null;
                Map<String, Object> imageInfo = new HashMap<>();
                imageInfo.put("index", i);
                imageInfo.put("original", img == null || StrUtil.isBlank(img.getString("original"))
                        ? null : controlledImageUrl(postId, i, "original"));
                imageInfo.put("compressed", controlledImageUrl(postId, i, "compressed"));
                imageInfo.put("thumbnail", img == null || StrUtil.isBlank(img.getString("thumbnail"))
                        ? controlledImageUrl(postId, i, "compressed")
                        : controlledImageUrl(postId, i, "thumbnail"));
                imageInfo.put("originalSize", img == null ? null : img.getString("originalSize"));
                imageInfo.put("compressedSize", img == null ? null : img.getString("compressedSize"));
                imageList.add(imageInfo);
            }
        } catch (Exception e) {
            log.error("event=post_image_metadata_parse_failed postId={}", postId);
        }

        return imageList;
    }

    @Override
    public String getOriginalImageUrl(Long postId, Integer index, Long viewerId) {
        if (postId == null) {
            return null;
        }
        musicPostService.requirePostReadable(postId, viewerId);

        MusicPost post = musicPostMapper.selectById(postId);
        if (post == null || StrUtil.isBlank(post.getImages())) {
            return null;
        }

        try {
            List<com.alibaba.fastjson2.JSONObject> images = JSON.parseArray(post.getImages()).toJavaList(com.alibaba.fastjson2.JSONObject.class);
            if (index != null && index >= 0 && index < images.size()) {
                return controlledImageUrl(postId, index, "original");
            }
        } catch (Exception e) {
            log.error("event=post_image_original_url_query_failed postId={} index={}", postId, index);
        }

        return null;
    }

    @Override
    public void deliverPostImage(Long postId, Integer index, String variant, Long viewerId,
                                 javax.servlet.http.HttpServletResponse response) {
        musicPostService.requirePostReadable(postId, viewerId);
        MusicPost post = musicPostMapper.selectById(postId);
        String normalizedVariant = normalizeImageVariant(variant);
        String sourceUrl = imageSourceUrl(post, index, normalizedVariant);
        String sourcePath = validatedPostImageUriPath(sourceUrl, normalizedVariant);
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Accel-Redirect", "/__post_media" + sourcePath);
    }

    private String controlledImageUrl(Long postId, Integer index, String variant) {
        return "/api/music-square/posts/" + postId + "/images/" + index
                + "/content?variant=" + variant;
    }

    private String normalizeImageVariant(String variant) {
        if ("original".equals(variant) || "compressed".equals(variant) || "thumbnail".equals(variant)) {
            return variant;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的图片变体");
    }

    private String imageSourceUrl(MusicPost post, Integer index, String variant) {
        if (post == null || StrUtil.isBlank(post.getImages()) || index == null || index < 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "动态图片不存在");
        }
        try {
            List<Object> images = JSON.parseArray(post.getImages(), Object.class);
            if (index >= images.size()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "动态图片不存在");
            }
            Object stored = images.get(index);
            String sourceUrl;
            if (stored instanceof String) {
                sourceUrl = "compressed".equals(variant) ? (String) stored : null;
            } else {
                sourceUrl = ((com.alibaba.fastjson2.JSONObject) stored).getString(variant);
            }
            if (StrUtil.isBlank(sourceUrl)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "动态图片不存在");
            }
            return sourceUrl;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.NOT_FOUND, "动态图片不存在");
        }
    }

    private String validatedPostImageUriPath(String sourceUrl, String variant) {
        try {
            String path = java.net.URI.create(sourceUrl).getPath();
            String expectedPrefix = "/posts/images/" + variant + "/";
            if (path == null || !path.startsWith(expectedPrefix) || path.contains("..") || path.contains("\\")) {
                throw new BusinessException(ResultCode.FORBIDDEN, "动态图片来源不受信任");
            }
            return path;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.FORBIDDEN, "动态图片来源不受信任");
        }
    }

    @Override
    public boolean deletePostImages(Long postId) {
        if (postId == null) {
            return false;
        }

        try {
                     
            MusicPost post = musicPostMapper.selectById(postId);
            if (post == null || StrUtil.isBlank(post.getImages())) {
                return true;
            }

                     
            List<com.alibaba.fastjson2.JSONObject> images = JSON.parseArray(post.getImages()).toJavaList(com.alibaba.fastjson2.JSONObject.class);

                           
            int deletedCount = 0;
            for (com.alibaba.fastjson2.JSONObject img : images) {
                String original = img.getString("original");
                String compressed = img.getString("compressed");
                String thumbnail = img.getString("thumbnail");

                            
                deletedCount += deleteImageFile(original);
                deletedCount += deleteImageFile(compressed);
                deletedCount += deleteImageFile(thumbnail);
            }

                          
            post.setImages(null);
            musicPostMapper.updateById(post);

            log.info("event=post_image_delete_completed postId={} deletedCount={}", postId, deletedCount);
            return true;

        } catch (Exception e) {
            log.error("event=post_image_delete_failed postId={}", postId);
            return false;
        }
    }

       
             
       
    private int deleteImageFile(String imageUrl) {
        if (StrUtil.isBlank(imageUrl)) {
            return 0;
        }

        try {
            Path filePath = resolveManagedPostImagePath(imageUrl);
            if (filePath == null || !Files.isRegularFile(filePath, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(filePath)) {
                return 0;
            }
            if (Files.deleteIfExists(filePath)) {
                log.debug("event=post_image_delete_file_succeeded");
                return 1;
            }
        } catch (Exception e) {
            log.warn("event=post_image_delete_file_failed errorType={}", e.getClass().getSimpleName());
        }

        return 0;
    }

       
              
       
    private Path resolveManagedPostImagePath(String imageUrl) {
        String mappedPath = CommonUtil.extractLocalPath(imageUrl);
        if (StrUtil.isNotBlank(mappedPath)) {
            Path validated = validateManagedImagePath(Paths.get(mappedPath));
            if (validated != null) {
                return validated;
            }
        }

        String relativePath = imageUrl;
        String urlPrefix = postMediaConfig.getNginxUrlPrefix();
        if (StrUtil.isNotBlank(urlPrefix) && imageUrl.startsWith(urlPrefix)) {
            relativePath = imageUrl.substring(urlPrefix.length());
        }
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return resolvePostImagePath(relativePath);
    }

    private Path resolvePostImagePath(String relativePath) {
        if (StrUtil.isBlank(relativePath)) {
            return null;
        }
        if (relativePath.startsWith("posts/images/original/")) {
            return resolveWithinManagedRoot(postMediaConfig.getImageOriginalPath(),
                    relativePath.substring("posts/images/original/".length()));
        }
        if (relativePath.startsWith("posts/images/compressed/")) {
            return resolveWithinManagedRoot(postMediaConfig.getImageCompressedPath(),
                    relativePath.substring("posts/images/compressed/".length()));
        }
        if (relativePath.startsWith("posts/images/thumbnails/")) {
            return resolveWithinManagedRoot(postMediaConfig.getImageThumbnailPath(),
                    relativePath.substring("posts/images/thumbnails/".length()));
        }
        return null;
    }

    private Path resolveWithinManagedRoot(String rootPath, String relativePath) {
        if (StrUtil.isBlank(rootPath) || StrUtil.isBlank(relativePath)) {
            return null;
        }
        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        Path candidate = root.resolve(relativePath).normalize();
        return candidate.startsWith(root) ? validateManagedImagePath(candidate) : null;
    }

    private Path validateManagedImagePath(Path candidate) {
        if (candidate == null) {
            return null;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        for (String configuredRoot : Arrays.asList(
                postMediaConfig.getImageOriginalPath(),
                postMediaConfig.getImageCompressedPath(),
                postMediaConfig.getImageThumbnailPath())) {
            if (StrUtil.isBlank(configuredRoot)) {
                continue;
            }
            Path root = Paths.get(configuredRoot).toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) {
                continue;
            }
            try {
                if (!Files.exists(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        || !Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    return normalized;
                }
                Path realRoot = root.toRealPath();
                Path realCandidate = normalized.toRealPath();
                return realCandidate.startsWith(realRoot) ? normalized : null;
            } catch (IOException exception) {
                return null;
            }
        }
        return null;
    }

    private Map<String, String> uploadImage(MultipartFile file, String baseName) {
        Map<String, String> result = new HashMap<>();
        Path originalTarget = null;
        Path compressedTarget = null;
        Path thumbnailTarget = null;

        try {
                      
            validateImageFile(file);

                         
            String extension = getFileExtension(file.getOriginalFilename());

                        
            String originalFilePath = postMediaConfig.getImageOriginalPath() + baseName + "_original" + extension;
            String compressedFilePath = postMediaConfig.getImageCompressedPath() + baseName + "_compressed" + extension;
            String thumbnailFilePath = postMediaConfig.getImageThumbnailPath() + baseName + "_thumb" + extension;

                        
            Files.createDirectories(Paths.get(originalFilePath).getParent());
            Files.createDirectories(Paths.get(compressedFilePath).getParent());
            Files.createDirectories(Paths.get(thumbnailFilePath).getParent());

                      
            originalTarget = Paths.get(originalFilePath);
            compressedTarget = Paths.get(compressedFilePath);
            thumbnailTarget = Paths.get(thumbnailFilePath);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, originalTarget, StandardCopyOption.REPLACE_EXISTING);
            }

                                                                                                                     
            File originalFile = originalTarget.toFile();
            FileSecurityUtil.SecurityCheckResult securityResult = FileSecurityUtil.checkImageSecurity(
                    originalFile,
                    file.getContentType(),
                    postMediaConfig.getImageMaxFileSize(),
                    musicUploadConfig.getAllowedImageTypes(),
                    musicUploadConfig.getImageMaxWidth(),
                    musicUploadConfig.getImageMaxHeight());
            if (!securityResult.isSafe()) {
                originalFile.delete();
                throw new SecurityException("动态图片安全检测失败");
            }
            if (musicUploadConfig.isVirusScanEnabled() && !virusScanService.scanFile(originalFile)) {
                originalFile.delete();
                throw new SecurityException("post image virus scan failed");
            }

                             
            CompressUtil.CompressResult compressedResult = CompressUtil.compressImage(
                    originalFilePath,
                    compressedFilePath,
                    postMediaConfig.getImageCompressedQuality()
            );
            if (!compressedResult.isSuccess()
                    || !Files.exists(Paths.get(compressedFilePath))
                    || Files.size(Paths.get(compressedFilePath)) <= 0) {
                Files.deleteIfExists(Paths.get(originalFilePath));
                Files.deleteIfExists(Paths.get(compressedFilePath));
                throw new IllegalStateException("动态图片压缩失败");
            }

                                          
            CompressUtil.CompressResult thumbnailResult = CompressUtil.compressImage(
                    originalFilePath,
                    thumbnailFilePath,
                    postMediaConfig.getImageThumbnailQuality()
            );
            if (!thumbnailResult.isSuccess()
                    || !Files.exists(Paths.get(thumbnailFilePath))
                    || Files.size(Paths.get(thumbnailFilePath)) <= 0) {
                Files.deleteIfExists(Paths.get(originalFilePath));
                Files.deleteIfExists(Paths.get(compressedFilePath));
                Files.deleteIfExists(Paths.get(thumbnailFilePath));
                throw new IllegalStateException("动态图片缩略图生成失败");
            }

                            
            String originalUrl = postMediaConfig.getNginxUrlPrefix() + "posts/images/original/" + baseName + "_original" + extension;
            String compressedUrl = postMediaConfig.getNginxUrlPrefix() + "posts/images/compressed/" + baseName + "_compressed" + extension;
            String thumbnailUrl = postMediaConfig.getNginxUrlPrefix() + "posts/images/thumbnails/" + baseName + "_thumb" + extension;

            result.put("original", originalUrl);
            result.put("compressed", compressedUrl);
            result.put("thumbnail", thumbnailUrl);
            result.put("originalSize", formatSize(file.getSize()));
            result.put("compressedSize", formatSize(compressedResult.getCompressedSize()));
            result.put("compressionRatio", String.format("%.1f%%", compressedResult.getCompressionRatio()));

            log.info("event=post_image_upload_succeeded originalBytes={} compressedBytes={} compressionPercent={}",
                    file.getSize(),
                    compressedResult.getCompressedSize(),
                    compressedResult.getCompressionRatio());

            return result;

        } catch (Exception e) {
            log.error("event=post_image_upload_failed errorType={}", e.getClass().getSimpleName());
            deleteIfExists(originalTarget);
            deleteIfExists(compressedTarget);
            deleteIfExists(thumbnailTarget);
            result.put("error", "上传失败，请检查文件后重试");
            return result;
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupError) {
            log.warn("event=post_image_partial_cleanup_failed");
        }
    }

       
             
       
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

                 
        if (file.getSize() > postMediaConfig.getImageMaxFileSize()) {
            throw new IllegalArgumentException("图片大小不能超过10MB");
        }

                 
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支持图片文件");
        }
    }

       
              
       
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return ".jpg";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return ".jpg";
        }
        String ext = filename.substring(lastDotIndex).toLowerCase();
                            
        if (isAllowedImageExtension(ext)) {
            return ext;
        }
        return ".jpg";
    }

       
              
       
       
                                                                      
      
  
    private boolean isAllowedImageExtension(String extension) {
        return postMediaConfig.getImageAllowedExtensions() != null
                && postMediaConfig.getImageAllowedExtensions().contains(extension);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
