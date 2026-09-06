package com.haoran.music.service.impl;

import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.FileSecurityUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.MediaAsset;
import com.haoran.music.entity.MediaAssetReference;
import com.haoran.music.entity.MediaUploadSession;
import com.haoran.music.entity.Message;
import com.haoran.music.enums.PrivateAttachmentPurpose;
import com.haoran.music.mapper.MediaAssetMapper;
import com.haoran.music.mapper.MediaAssetReferenceMapper;
import com.haoran.music.mapper.MediaUploadSessionMapper;
import com.haoran.music.mapper.MessageMapper;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.PrivateAttachmentGrantService;
import com.haoran.music.service.PrivateAttachmentService;
import com.haoran.music.service.VirusScanService;
import com.haoran.music.vo.attachment.PrivateAttachmentAssetVO;
import com.haoran.music.vo.attachment.PrivateAttachmentDownload;
import com.haoran.music.vo.attachment.PrivateAttachmentGrantVO;
import com.haoran.music.vo.attachment.PrivateAttachmentSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;






@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateAttachmentServiceImpl implements PrivateAttachmentService {

    private static final String SOURCE_TYPE = "PRIVATE_ATTACHMENT";
    private static final String REFERENCE_ROLE = "ATTACHMENT";

    private final MediaUploadSessionMapper mediaUploadSessionMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaAssetReferenceMapper mediaAssetReferenceMapper;
    private final MessageMapper messageMapper;
    private final MediaAssetService mediaAssetService;
    private final PermissionService permissionService;
    private final PrivateAttachmentGrantService grantService;
    private final VirusScanService virusScanService;
    private final MusicUploadConfig uploadConfig;








    @Override
    public PrivateAttachmentSessionVO createSession(Long ownerId, String purpose) {
        requireUser(ownerId);
        PrivateAttachmentPurpose attachmentPurpose = PrivateAttachmentPurpose.require(purpose);
        LocalDateTime now = LocalDateTime.now();

        MediaUploadSession session = new MediaUploadSession();
        session.setSessionToken(UUID.randomUUID().toString());
        session.setOwnerId(ownerId);
        session.setPurpose(attachmentPurpose.name());
        session.setStatus(MediaUploadSession.STATUS_OPEN);
        session.setMaxFiles(attachmentPurpose.getMaxFiles());
        session.setUploadedCount(0);
        session.setExpiresAt(now.plusMinutes(safeSessionMinutes()));
        session.setCreateTime(now);
        session.setUpdateTime(now);
        mediaUploadSessionMapper.insert(session);
        return toSessionView(session);
    }









    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrivateAttachmentAssetVO upload(Long ownerId, String sessionToken, MultipartFile file) {
        requireUser(ownerId);
        MediaUploadSession session = requireOwnedOpenSession(ownerId, sessionToken);
        validateClientFile(file);
        if (mediaUploadSessionMapper.reserveSlot(session.getId(), ownerId, LocalDateTime.now()) != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传会话已过期或文件数量已达上限");
        }

        Path tempFile = null;
        Path storedFile = null;
        try {
            Path root = ensurePrivateRoot();
            Path tempRoot = Files.createDirectories(root.resolve(".tmp"));
            tempFile = Files.createTempFile(tempRoot, "attachment-", ".upload");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            ImageType imageType = detectAndValidateImage(file, tempFile);
            scanOrReject(tempFile, file.getOriginalFilename());
            String fileHash = sha256(tempFile);

            MediaAsset existing = mediaAssetMapper.selectPrivateBySessionHash(session.getId(), fileHash);
            if (ObjectUtils.isNotEmpty(existing)) {
                mediaUploadSessionMapper.releaseSlot(session.getId(), ownerId);
                return toAssetView(existing);
            }

            Path ownerRoot = root.resolve(String.valueOf(ownerId)).resolve(session.getSessionToken()).normalize();
            if (!ownerRoot.startsWith(root)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "私有附件存储路径不合法");
            }
            Files.createDirectories(ownerRoot);
            storedFile = ownerRoot.resolve(UUID.randomUUID().toString().replace("-", "")
                    + "." + imageType.extension).normalize();
            Files.move(tempFile, storedFile);
            tempFile = null;

            MediaAsset asset = buildAsset(ownerId, session, file, storedFile, fileHash, imageType);
            if (mediaAssetMapper.insertPrivateAssetIfAbsent(asset) == 0) {
                deleteQuietly(storedFile);
                storedFile = null;
                mediaUploadSessionMapper.releaseSlot(session.getId(), ownerId);
                existing = mediaAssetMapper.selectPrivateBySessionHash(session.getId(), fileHash);
                if (ObjectUtils.isEmpty(existing)) {
                    throw new BusinessException("附件幂等登记失败，请重试");
                }
                return toAssetView(existing);
            }

            log.info("event=private_attachment_stored assetId={} ownerId={} purpose={} size={}",
                    asset.getId(), ownerId, session.getPurpose(), asset.getFileSize());
            return toAssetView(asset);
        } catch (BusinessException e) {
            deleteQuietly(storedFile);
            throw e;
        } catch (Exception e) {
            deleteQuietly(storedFile);
            log.warn("event=private_attachment_upload_failed ownerId={} errorType={}",
                    ownerId, e.getClass().getSimpleName());
            throw new BusinessException("私有附件上传失败，请稍后重试");
        } finally {
            deleteQuietly(tempFile);
        }
    }







    @Override
    public void cancelSession(Long ownerId, String sessionToken) {
        requireUser(ownerId);
        MediaUploadSession session = requireOwnedSession(ownerId, sessionToken);
        if (MediaUploadSession.STATUS_CANCELLED.equals(session.getStatus())
                || MediaUploadSession.STATUS_EXPIRED.equals(session.getStatus())) {
            return;
        }
        if (!MediaUploadSession.STATUS_OPEN.equals(session.getStatus())) {
            throw new BusinessException("已绑定的上传会话不能取消");
        }
        mediaUploadSessionMapper.cancel(session.getId(), ownerId);
        mediaAssetMapper.expireUnboundSessionAssets(session.getId(),
                LocalDateTime.now().plusHours(safeReclaimGraceHours()));
    }










    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindAssets(Long ownerId, String purpose, List<Long> assetIds,
                           String targetType, Long targetId) {
        requireUser(ownerId);
        PrivateAttachmentPurpose attachmentPurpose = PrivateAttachmentPurpose.require(purpose);
        if (!attachmentPurpose.getTargetType().equals(targetType) || ObjectUtils.isEmpty(targetId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件用途与业务类型不匹配");
        }
        List<Long> uniqueAssetIds = normalizeAssetIds(assetIds, attachmentPurpose.getMaxFiles());
        if (uniqueAssetIds.isEmpty()) {
            return;
        }

        Long sessionId = null;
        List<MediaAsset> assets = new ArrayList<MediaAsset>();
        for (Long assetId : uniqueAssetIds) {
            MediaAsset asset = mediaAssetMapper.selectPrivateForUpdate(assetId);
            requireBindableAsset(asset, ownerId, attachmentPurpose);
            if (ObjectUtils.isEmpty(sessionId)) {
                sessionId = asset.getUploadSessionId();
            } else if (!sessionId.equals(asset.getUploadSessionId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "一次业务提交只能绑定同一上传会话的附件");
            }
            assets.add(asset);
        }
        if (mediaUploadSessionMapper.claimTarget(sessionId, ownerId, targetType, targetId,
                LocalDateTime.now()) != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "上传会话已过期或已绑定其他业务记录");
        }
        for (MediaAsset asset : assets) {
            Long assetId = asset.getId();
            List<MediaAssetReference> references = mediaAssetReferenceMapper.selectActiveByAsset(assetId);
            requireNoConflictingReference(references, targetType, targetId);
            mediaAssetReferenceMapper.retainActive(assetId, targetType, targetId, REFERENCE_ROLE);
            mediaAssetMapper.clearGraceUntil(assetId);
        }
    }








    @Override
    public List<Long> listTargetAssetIds(String targetType, Long targetId) {
        if (ObjectUtils.isEmpty(targetType) || ObjectUtils.isEmpty(targetId)) {
            return Collections.emptyList();
        }
        List<Long> assetIds = mediaAssetReferenceMapper.selectActiveAssetIdsByTarget(targetType, targetId);
        return ObjectUtils.isEmpty(assetIds) ? Collections.emptyList() : assetIds;
    }








    @Override
    public PrivateAttachmentGrantVO issueGrant(Long assetId, Long viewerId) {
        requireAccessibleAsset(assetId, viewerId);
        String grant = grantService.issue(assetId, viewerId);
        String url = "/api/private-attachments/assets/" + assetId + "/content?grant=" + grant;
        return new PrivateAttachmentGrantVO(assetId, grant, url);
    }









    @Override
    public PrivateAttachmentDownload loadForDownload(Long assetId, Long viewerId, String grant) {
        Long authorizedUserId = viewerId;
        if (ObjectUtils.isEmpty(authorizedUserId)) {
            authorizedUserId = grantService.verify(grant, assetId);
        }
        MediaAsset asset = requireAccessibleAsset(assetId, authorizedUserId);
        Path file = resolveManagedFile(asset);
        return new PrivateAttachmentDownload(new FileSystemResource(file.toFile()),
                asset.getContentType(), safeOriginalName(asset.getOriginalName()), asset.getFileSize());
    }







    @Override
    public void releaseTargetReferences(String targetType, Long targetId) {
        mediaAssetService.releaseTargetReferences(targetType, targetId);
    }







    @Override
    public int expireSessions(int limit) {
        return mediaUploadSessionMapper.expireSessions(Math.max(1, Math.min(limit, 1000)));
    }












    private MediaAsset buildAsset(Long ownerId, MediaUploadSession session, MultipartFile file,
                                  Path storedFile, String fileHash, ImageType imageType) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        MediaAsset asset = new MediaAsset();
        asset.setOwnerId(ownerId);
        asset.setUploadSessionId(session.getId());
        asset.setPurpose(session.getPurpose());
        asset.setVisibility(MediaAsset.VISIBILITY_PRIVATE);
        asset.setOriginalName(safeOriginalName(file.getOriginalFilename()));
        asset.setContentType(imageType.contentType);
        asset.setMediaType("image");
        asset.setSourceType(SOURCE_TYPE);
        asset.setSourceId(session.getId());
        asset.setAssetRole(session.getPurpose());
        asset.setPublicUrl(null);
        asset.setStorageNode(MediaAsset.STORAGE_NODE_LOCAL);
        asset.setStoragePath(storedFile.toAbsolutePath().normalize().toString());
        asset.setFileHash(fileHash);
        asset.setFileSize(Files.size(storedFile));
        asset.setScanStatus(MediaAsset.SCAN_STATUS_CLEAN);
        asset.setStatus(MediaAsset.STATUS_ACTIVE);
        asset.setGraceUntil(session.getExpiresAt().plusHours(safeReclaimGraceHours()));
        asset.setCreateTime(now);
        asset.setUpdateTime(now);
        return asset;
    }






    private void validateClientFile(MultipartFile file) {
        if (ObjectUtils.isEmpty(file) || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件不能为空");
        }
        if (file.getSize() > uploadConfig.getImageMaxFileSize()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件大小超过限制");
        }
        String extension = originalExtension(file.getOriginalFilename());
        if (!uploadConfig.getAllowedImageExtensions().contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件扩展名不受支持");
        }
    }








    private ImageType detectAndValidateImage(MultipartFile file, Path tempFile) throws IOException {
        String declaredType = ObjectUtils.isEmpty(file.getContentType())
                ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!uploadConfig.isAllowedImageType(declaredType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件MIME类型不受支持");
        }
        ImageType actualType = detectImageType(tempFile);
        if (!actualType.matches(declaredType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件声明类型与实际内容不一致");
        }
        FileSecurityUtil.SecurityCheckResult result = FileSecurityUtil.checkImageSecurity(
                tempFile.toFile(), actualType.contentType, uploadConfig.getImageMaxFileSize(),
                uploadConfig.getAllowedImageTypes(), uploadConfig.getImageMaxWidth(),
                uploadConfig.getImageMaxHeight());
        if (!result.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, result.getMessage());
        }
        return actualType;
    }







    private void scanOrReject(Path file, String originalName) {
        if (!uploadConfig.isVirusScanEnabled()) {
            return;
        }
        try {
            if (!virusScanService.scanFile(file.toFile())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "附件病毒扫描未通过");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("event=private_attachment_virus_scan_unavailable errorType={}",
                    e.getClass().getSimpleName());
            throw new BusinessException("附件安全扫描暂不可用，请稍后重试");
        }
    }








    private void requireBindableAsset(MediaAsset asset, Long ownerId, PrivateAttachmentPurpose purpose) {
        if (ObjectUtils.isEmpty(asset)
                || !ownerId.equals(asset.getOwnerId())
                || !purpose.name().equals(asset.getPurpose())
                || !MediaAsset.STATUS_ACTIVE.equals(asset.getStatus())
                || !MediaAsset.SCAN_STATUS_CLEAN.equals(asset.getScanStatus())
                || !MediaAsset.VISIBILITY_PRIVATE.equals(asset.getVisibility())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "附件不存在、无权使用或尚未通过安全扫描");
        }
    }








    private void requireNoConflictingReference(List<MediaAssetReference> references,
                                               String targetType, Long targetId) {
        if (ObjectUtils.isEmpty(references)) {
            return;
        }
        for (MediaAssetReference reference : references) {
            if (!targetType.equals(reference.getTargetType()) || !targetId.equals(reference.getTargetId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "附件已被其他业务记录使用");
            }
        }
    }








    private MediaAsset requireAccessibleAsset(Long assetId, Long viewerId) {
        requireUser(viewerId);
        MediaAsset asset = mediaAssetMapper.selectById(assetId);
        if (ObjectUtils.isEmpty(asset)
                || !MediaAsset.VISIBILITY_PRIVATE.equals(asset.getVisibility())
                || !MediaAsset.STATUS_ACTIVE.equals(asset.getStatus())
                || !MediaAsset.SCAN_STATUS_CLEAN.equals(asset.getScanStatus())) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "私有附件不存在或已回收");
        }
        List<MediaAssetReference> references = mediaAssetReferenceMapper.selectActiveByAsset(assetId);
        if (viewerId.equals(asset.getOwnerId())) {
            if (ObjectUtils.isNotEmpty(references) || isOpenPreviewSession(asset)) {
                return asset;
            }
            throw new BusinessException(ResultCode.FORBIDDEN, "私有附件已释放或上传会话已过期");
        }
        for (MediaAssetReference reference : references) {
            if (canAccessReference(reference, viewerId)) {
                return asset;
            }
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问此私有附件");
    }








    private boolean canAccessReference(MediaAssetReference reference, Long viewerId) {
        String targetType = reference.getTargetType();
        if (PrivateAttachmentPurpose.MESSAGE_IMAGE.getTargetType().equals(targetType)) {
            Message message = messageMapper.selectById(reference.getTargetId());
            return ObjectUtils.isNotEmpty(message)
                    && (viewerId.equals(message.getSenderId()) || viewerId.equals(message.getReceiverId()))
                    && !Integer.valueOf(1).equals(message.getIsRecalled());
        }
        if (PrivateAttachmentPurpose.REPORT_EVIDENCE.getTargetType().equals(targetType)
                || PrivateAttachmentPurpose.MODERATION_APPEAL_EVIDENCE.getTargetType().equals(targetType)) {
            return permissionService.isModerator(viewerId);
        }
        if (PrivateAttachmentPurpose.FEEDBACK_ATTACHMENT.getTargetType().equals(targetType)
                || PrivateAttachmentPurpose.APPEAL_EVIDENCE.getTargetType().equals(targetType)) {
            return permissionService.isAdmin(viewerId);
        }
        return false;
    }







    private boolean isOpenPreviewSession(MediaAsset asset) {
        MediaUploadSession session = mediaUploadSessionMapper.selectById(asset.getUploadSessionId());
        return ObjectUtils.isNotEmpty(session)
                && MediaUploadSession.STATUS_OPEN.equals(session.getStatus())
                && LocalDateTime.now().isBefore(session.getExpiresAt());
    }








    private MediaUploadSession requireOwnedOpenSession(Long ownerId, String sessionToken) {
        MediaUploadSession session = requireOwnedSession(ownerId, sessionToken);
        if (!MediaUploadSession.STATUS_OPEN.equals(session.getStatus())
                || !LocalDateTime.now().isBefore(session.getExpiresAt())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传会话已关闭或过期");
        }
        return session;
    }








    private MediaUploadSession requireOwnedSession(Long ownerId, String sessionToken) {
        if (ObjectUtils.isEmpty(sessionToken) || sessionToken.length() > 64) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传会话令牌不合法");
        }
        MediaUploadSession session = mediaUploadSessionMapper.selectByToken(sessionToken);
        if (ObjectUtils.isEmpty(session) || !ownerId.equals(session.getOwnerId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "上传会话不存在或无权访问");
        }
        return session;
    }








    private List<Long> normalizeAssetIds(List<Long> assetIds, int maxFiles) {
        if (ObjectUtils.isEmpty(assetIds)) {
            return Collections.emptyList();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long assetId : assetIds) {
            if (ObjectUtils.isEmpty(assetId) || assetId <= 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "附件资产ID不合法");
            }
            uniqueIds.add(assetId);
        }
        if (uniqueIds.size() > maxFiles) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件数量超过用途限制");
        }
        return new ArrayList<>(uniqueIds);
    }







    private Path resolveManagedFile(MediaAsset asset) {
        try {
            Path root = ensurePrivateRoot().toRealPath();
            Path file = Paths.get(asset.getStoragePath()).toRealPath();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "私有附件路径不受信任");
            }
            return file;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "私有附件文件不存在");
        }
    }






    private Path ensurePrivateRoot() throws IOException {
        String configuredPath = uploadConfig.getPrivateAttachmentPath();
        if (ObjectUtils.isEmpty(configuredPath)) {
            throw new IllegalStateException("private attachment path is not configured");
        }
        return Files.createDirectories(Paths.get(configuredPath).toAbsolutePath().normalize());
    }







    private ImageType detectImageType(Path file) throws IOException {
        byte[] header = new byte[12];
        int length;
        try (InputStream inputStream = Files.newInputStream(file)) {
            length = inputStream.read(header);
        }
        if (length >= 3 && unsigned(header[0]) == 0xFF && unsigned(header[1]) == 0xD8
                && unsigned(header[2]) == 0xFF) {
            return new ImageType("jpg", "image/jpeg");
        }
        if (length >= 8 && unsigned(header[0]) == 0x89 && header[1] == 'P'
                && header[2] == 'N' && header[3] == 'G') {
            return new ImageType("png", "image/png");
        }
        if (length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
            return new ImageType("gif", "image/gif");
        }
        if (length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                && header[10] == 'B' && header[11] == 'P') {
            return new ImageType("webp", "image/webp");
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "无法识别附件图片格式");
    }







    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = Files.newInputStream(file)) {
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) {
            value.append(String.format("%02x", item & 0xFF));
        }
        return value.toString();
    }







    private String originalExtension(String filename) {
        String safeName = safeOriginalName(filename);
        int dot = safeName.lastIndexOf('.');
        return dot < 0 ? "" : safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }







    private String safeOriginalName(String filename) {
        String value = ObjectUtils.isEmpty(filename) ? "attachment" : filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isEmpty()) {
            value = "attachment";
        }
        return value.length() > 200 ? value.substring(value.length() - 200) : value;
    }






    private void deleteQuietly(Path path) {
        if (ObjectUtils.isEmpty(path)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("event=private_attachment_compensation_delete_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }






    private void requireUser(Long userId) {
        if (ObjectUtils.isEmpty(userId) || userId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }






    private int safeSessionMinutes() {
        return Math.max(5, Math.min(uploadConfig.getPrivateAttachmentSessionMinutes(), 120));
    }






    private int safeReclaimGraceHours() {
        return Math.max(1, Math.min(uploadConfig.getPrivateAttachmentReclaimGraceHours(), 168));
    }







    private PrivateAttachmentSessionVO toSessionView(MediaUploadSession session) {
        PrivateAttachmentSessionVO view = new PrivateAttachmentSessionVO();
        view.setSessionToken(session.getSessionToken());
        view.setPurpose(session.getPurpose());
        view.setMaxFiles(session.getMaxFiles());
        view.setExpiresAt(session.getExpiresAt());
        return view;
    }







    private PrivateAttachmentAssetVO toAssetView(MediaAsset asset) {
        PrivateAttachmentAssetVO view = new PrivateAttachmentAssetVO();
        view.setAssetId(asset.getId());
        view.setPurpose(asset.getPurpose());
        view.setContentType(asset.getContentType());
        view.setFileSize(asset.getFileSize());
        view.setScanStatus(asset.getScanStatus());
        return view;
    }







    private int unsigned(byte value) {
        return value & 0xFF;
    }





    private static final class ImageType {
        private final String extension;
        private final String contentType;

        private ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }







        private boolean matches(String declaredType) {
            return contentType.equals(declaredType)
                    || ("image/jpeg".equals(contentType) && "image/jpg".equals(declaredType));
        }
    }
}
