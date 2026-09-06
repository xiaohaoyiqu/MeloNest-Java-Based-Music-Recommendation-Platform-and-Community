package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.entity.MediaAsset;
import com.haoran.music.mapper.MediaAssetMapper;
import com.haoran.music.mapper.MediaAssetReferenceMapper;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.Node3MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;






@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAssetServiceImpl implements MediaAssetService {

    private static final String REFERENCE_ROLE_ACTIVE = "active";
    private static final int RECLAIM_GRACE_HOURS = 24;

    private final MediaAssetMapper mediaAssetMapper;
    private final MediaAssetReferenceMapper mediaAssetReferenceMapper;
    private final Node3MediaService node3MediaService;
    private final ObjectMapper objectMapper;

    @Value("${music.upload.multi-file.path:}")
    private String multiFilePath;

    @Value("${music.upload.nginx.url:}")
    private String multiFileNginxUrl;

    @Value("${music.upload.nginx-url-prefix:}")
    private String uploadNginxUrlPrefix;

    @Value("${music.upload.private-attachment-path:${java.io.tmpdir}/haoran-private-attachments}")
    private String privateAttachmentPath;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerStagedSubmissionAsset(Long ownerId, String mediaType, String originalName,
                                              String publicUrl, String storagePath, Long fileSize) {
        if (ObjectUtils.isEmpty(ownerId) || StrUtil.isBlank(mediaType) || StrUtil.isBlank(publicUrl)
                || StrUtil.isBlank(storagePath)) {
            throw new BusinessException("暂存媒体资产身份不完整");
        }
        MediaAsset existing = selectAssetForUpdate(
                MediaAsset.STORAGE_NODE_LOCAL, storagePath, publicUrl);
        if (ObjectUtils.isNotEmpty(existing)) {
            requireReusableAsset(existing, ownerId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        MediaAsset asset = new MediaAsset();
        asset.setOwnerId(ownerId);
        asset.setOriginalName(safeOriginalName(originalName));
        asset.setMediaType(mediaType);
        asset.setSourceType("submission_staging");
        asset.setAssetRole("staged");
        asset.setPublicUrl(publicUrl);
        asset.setStorageNode(MediaAsset.STORAGE_NODE_LOCAL);
        asset.setStoragePath(storagePath);
        asset.setFileSize(fileSize);
        asset.setScanStatus(MediaAsset.SCAN_STATUS_CLEAN);
        asset.setVisibility(MediaAsset.VISIBILITY_PUBLIC);
        asset.setStatus(MediaAsset.STATUS_ACTIVE);
        asset.setGraceUntil(now.plusHours(RECLAIM_GRACE_HOURS));
        asset.setCreateTime(now);
        asset.setUpdateTime(now);
        try {
            requireSingleWrite(mediaAssetMapper.insert(asset), "暂存媒体资产登记失败");
        } catch (DuplicateKeyException exception) {
            MediaAsset concurrent = selectAssetForUpdate(
                    MediaAsset.STORAGE_NODE_LOCAL, storagePath, publicUrl);
            if (ObjectUtils.isEmpty(concurrent)) {
                throw exception;
            }
            requireReusableAsset(concurrent, ownerId);
            return;
        }
        log.info("event=media_asset_submission_staged assetId={} ownerId={} mediaType={}",
                asset.getId(), ownerId, mediaType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerAndRetain(String mediaType, String sourceType, Long sourceId, String assetRole,
                                  String publicUrl, String storageNode, String storagePath, Long fileSize,
                                  String targetType, Long targetId) {
        if (ObjectUtils.isEmpty(targetId) || StrUtil.isBlank(targetType)
                || (StrUtil.isBlank(publicUrl) && StrUtil.isBlank(storagePath))) {
            throw new BusinessException("媒体资产引用身份不完整");
        }

        MediaAsset asset = selectAssetForUpdate(storageNode, storagePath, publicUrl);
        LocalDateTime now = LocalDateTime.now();
        if (ObjectUtils.isEmpty(asset)) {
            asset = new MediaAsset();
            asset.setMediaType(mediaType);
            asset.setSourceType(sourceType);
            asset.setSourceId(sourceId);
            asset.setAssetRole(assetRole);
            asset.setPublicUrl(publicUrl);
            asset.setStorageNode(storageNode);
            asset.setStoragePath(storagePath);
            asset.setFileSize(fileSize);
            asset.setStatus(MediaAsset.STATUS_ACTIVE);
            asset.setCreateTime(now);
        } else {
            requireReusableAsset(asset, null);
            requireSameOfficialSource(asset, sourceType, sourceId, assetRole);
            asset.setMediaType(mediaType);
            asset.setSourceType(sourceType);
            asset.setSourceId(sourceId);
            asset.setAssetRole(assetRole);
            asset.setPublicUrl(publicUrl);
            asset.setStorageNode(storageNode);
            asset.setStoragePath(storagePath);
            asset.setFileSize(fileSize);
            asset.setStatus(MediaAsset.STATUS_ACTIVE);
            asset.setReclaimedAt(null);
            asset.setLastError(null);
        }
        asset.setGraceUntil(null);
        asset.setUpdateTime(now);

        if (ObjectUtils.isEmpty(asset.getId())) {
            try {
                requireSingleWrite(mediaAssetMapper.insert(asset), "媒体资产登记失败");
            } catch (DuplicateKeyException exception) {
                asset = selectAssetForUpdate(storageNode, storagePath, publicUrl);
                if (ObjectUtils.isEmpty(asset)) {
                    throw exception;
                }
                requireReusableAsset(asset, null);
                requireSameOfficialSource(asset, sourceType, sourceId, assetRole);
            }
        } else {
            requireSingleWrite(mediaAssetMapper.updateById(asset), "媒体资产更新失败");
        }
        retain(asset.getId(), targetType, targetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerSubmissionAssets(String mediaType, String sourceType, Long sourceId,
                                         String primaryUrl, String additionalUrls, String zipUrl, Long fileSize) {
        if (ObjectUtils.isEmpty(sourceId) || StrUtil.isBlank(sourceType)) {
            return;
        }

        Set<String> urls = new LinkedHashSet<>();
        addUrl(urls, primaryUrl);
        addUrl(urls, zipUrl);
        collectUrls(additionalUrls, urls);
        for (String url : urls) {
            String storagePath = resolveSubmissionPath(url);
            if (StrUtil.isBlank(storagePath)) {
                log.debug("event=media_asset_submission_url_unmanaged sourceType={} sourceId={}",
                        sourceType, sourceId);
                continue;
            }
            registerAndRetain(mediaType, sourceType, sourceId, "source", url,
                    MediaAsset.STORAGE_NODE_LOCAL, storagePath, fileSize,
                    sourceType, sourceId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseTargetReferences(String targetType, Long targetId) {
        if (StrUtil.isBlank(targetType) || ObjectUtils.isEmpty(targetId)) {
            throw new BusinessException("媒体资产释放目标不能为空");
        }
        int released = mediaAssetReferenceMapper.releaseByTarget(targetType, targetId);
        if (released > 0) {
            LocalDateTime graceUntil = LocalDateTime.now().plusHours(RECLAIM_GRACE_HOURS);
            mediaAssetMapper.markOrphanGraceUntil(targetType, targetId, graceUntil);
            log.info("event=media_asset_references_released referenceCount={} targetType={} targetId={}",
                    released, targetType, targetId);
        }
    }

    @Override
    public int reclaimOrphanAssets(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<MediaAsset> candidates = mediaAssetMapper.selectReclaimCandidates(safeLimit);
        if (ObjectUtils.isEmpty(candidates)) {
            return 0;
        }

        int reclaimed = 0;
        for (MediaAsset asset : candidates) {
            if (ObjectUtils.isEmpty(asset) || mediaAssetMapper.claimForReclaim(asset.getId()) != 1) {
                continue;
            }
            if (deleteAsset(asset)) {
                markReclaimed(asset);
                reclaimed++;
            } else {
                markReclaimFailed(asset, "媒体文件不存在或不在受管目录");
            }
        }
        return reclaimed;
    }

    private MediaAsset selectAssetForUpdate(String storageNode, String storagePath, String publicUrl) {
        if (StrUtil.isNotBlank(storageNode) && StrUtil.isNotBlank(storagePath)) {
            MediaAsset asset = mediaAssetMapper.selectByStorageForUpdate(storageNode, storagePath);
            if (ObjectUtils.isNotEmpty(asset)) {
                return asset;
            }
        }
        return StrUtil.isBlank(publicUrl) ? null : mediaAssetMapper.selectByPublicUrlForUpdate(publicUrl);
    }








    private void retain(Long assetId, String targetType, Long targetId) {
        int inserted = mediaAssetReferenceMapper.retainActive(
                assetId, targetType, targetId, REFERENCE_ROLE_ACTIVE);
        if (inserted == 0 && ObjectUtils.isEmpty(mediaAssetReferenceMapper.selectActiveReference(
                assetId, targetType, targetId, REFERENCE_ROLE_ACTIVE))) {
            throw new BusinessException("媒体资产引用登记失败");
        }
    }







    private boolean deleteAsset(MediaAsset asset) {
        if (MediaAsset.STORAGE_NODE_MEDIA.equals(asset.getStorageNode())) {
            if (StrUtil.isBlank(asset.getStoragePath())) {
                return false;
            }
            return node3MediaService.deleteQuietly(asset.getStoragePath());
        }
        if (!MediaAsset.STORAGE_NODE_LOCAL.equals(asset.getStorageNode())
                || StrUtil.isBlank(asset.getStoragePath())) {
            return false;
        }

        String localPath = resolveAnyManagedLocalPath(asset.getStoragePath());
        if (StrUtil.isBlank(localPath)) {
            return false;
        }
        Path file = Paths.get(localPath);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            return Files.deleteIfExists(file);
        } catch (Exception exception) {
            return false;
        }
    }






    private void markReclaimed(MediaAsset asset) {
        requireSingleWrite(mediaAssetMapper.markReclaimedAfterClaim(asset.getId()),
                "媒体资产回收状态已变化");
        log.info("event=media_asset_reclaimed assetId={}", asset.getId());
    }







    private void markReclaimFailed(MediaAsset asset, String error) {
        requireSingleWrite(mediaAssetMapper.markReclaimFailedAfterClaim(asset.getId(), error),
                "媒体资产回收状态已变化");
        log.warn("event=media_asset_reclaim_failed assetId={} errorCategory={}",
                asset.getId(), safeErrorCategory(error));
    }

    private void requireReusableAsset(MediaAsset asset, Long expectedOwnerId) {
        if (MediaAsset.STATUS_RECLAIMING.equals(asset.getStatus())
                || MediaAsset.STATUS_RECLAIMED.equals(asset.getStatus())) {
            throw new BusinessException("媒体资产正在回收或已回收，请重新上传");
        }
        if (expectedOwnerId != null && asset.getOwnerId() != null
                && !expectedOwnerId.equals(asset.getOwnerId())) {
            throw new BusinessException("媒体资产归属不一致");
        }
    }




    private void requireSameOfficialSource(MediaAsset asset, String sourceType,
                                           Long sourceId, String assetRole) {
        boolean officialAsset = "official_derivative".equals(asset.getSourceType())
                || "official_derivative".equals(sourceType);
        if (officialAsset && (!"official_derivative".equals(asset.getSourceType())
                || !java.util.Objects.equals(asset.getSourceId(), sourceId)
                || !java.util.Objects.equals(asset.getAssetRole(), assetRole))) {
            throw new BusinessException("正式媒体资源存储路径归属冲突");
        }
    }

    private void requireSingleWrite(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new BusinessException(message);
        }
    }







    private String resolveSubmissionPath(String url) {
        if (StrUtil.isBlank(url) || StrUtil.isBlank(multiFilePath)) {
            return null;
        }

        String localPath = resolveFromPrefix(url, multiFileNginxUrl);
        if (StrUtil.isBlank(localPath)) {
            localPath = resolveFromPrefix(url, uploadNginxUrlPrefix);
        }
        if (StrUtil.isBlank(localPath)) {
            localPath = CommonUtil.extractLocalPath(url);
        }
        return resolveWithinRoot(localPath, multiFilePath);
    }








    private String resolveFromPrefix(String url, String prefix) {
        if (StrUtil.isBlank(url) || StrUtil.isBlank(prefix)) {
            return null;
        }
        String cleanPrefix = trimTrailingSlash(prefix);
        if (!url.startsWith(cleanPrefix + "/")) {
            return null;
        }
        String relative = url.substring(cleanPrefix.length() + 1);
        try {
            relative = URLDecoder.decode(relative, StandardCharsets.UTF_8.name()).replace('\\', '/');
            return Paths.get(multiFilePath).toAbsolutePath().normalize().resolve(relative).normalize().toString();
        } catch (Exception e) {
            log.warn("event=media_asset_submission_path_resolution_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }







    private String resolveAnyManagedLocalPath(String path) {
        String resolved = resolveWithinRoot(path, multiFilePath);
        return StrUtil.isNotBlank(resolved) ? resolved : resolveWithinRoot(path, privateAttachmentPath);
    }








    private String resolveWithinRoot(String path, String configuredRoot) {
        if (StrUtil.isBlank(path) || StrUtil.isBlank(configuredRoot) || !WorkProcessingUtil.isPathSafe(path)) {
            return null;
        }
        try {
            Path basePath = Paths.get(configuredRoot).toAbsolutePath().normalize();
            Path targetPath = Paths.get(path).toAbsolutePath().normalize();
            if (!targetPath.startsWith(basePath)) {
                return null;
            }
            if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)
                    && Files.exists(basePath, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(targetPath)
                        || !targetPath.toRealPath().startsWith(basePath.toRealPath())) {
                    return null;
                }
            }
            return targetPath.toString();
        } catch (Exception e) {
            return null;
        }
    }







    private void collectUrls(String rawUrls, Set<String> urls) {
        if (StrUtil.isBlank(rawUrls)) {
            return;
        }
        try {
            collectUrls(objectMapper.readTree(rawUrls), urls);
        } catch (Exception e) {
            for (String value : rawUrls.split("[,\\n]")) {
                addUrl(urls, value);
            }
        }
    }







    private void collectUrls(JsonNode node, Set<String> urls) {
        if (ObjectUtils.isEmpty(node) || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addUrl(urls, node.asText());
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectUrls(child, urls);
            }
            return;
        }
        if (node.isObject()) {
            addUrl(urls, firstText(node, "url", "fileUrl", "file_url", "path"));
            node.fields().forEachRemaining(entry -> collectUrls(entry.getValue(), urls));
        }
    }








    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node.get(field);
            if (ObjectUtils.isNotEmpty(child) && child.isTextual() && StrUtil.isNotBlank(child.asText())) {
                return child.asText();
            }
        }
        return null;
    }







    private void addUrl(Set<String> urls, String url) {
        if (StrUtil.isNotBlank(url)) {
            urls.add(url.trim());
        }
    }







    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String safeErrorCategory(String error) {
        return StrUtil.isBlank(error) ? "UNKNOWN" : error.replaceAll("[^A-Z0-9_]", "_");
    }

    private String safeOriginalName(String originalName) {
        if (StrUtil.isBlank(originalName)) {
            return "upload";
        }
        String sanitized = originalName.replace('\\', '/');
        int slash = sanitized.lastIndexOf('/');
        if (slash >= 0) {
            sanitized = sanitized.substring(slash + 1);
        }
        sanitized = sanitized.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (sanitized.isEmpty()) {
            return "upload";
        }
        return sanitized.length() > 200 ? sanitized.substring(sanitized.length() - 200) : sanitized;
    }
}
