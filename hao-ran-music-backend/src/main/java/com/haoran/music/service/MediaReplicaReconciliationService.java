package com.haoran.music.service;

import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.MediaAsset;
import com.haoran.music.mapper.MediaAssetMapper;
import com.haoran.music.mapper.MediaDerivativeTaskMapper;
import com.haoran.music.service.model.Node3DiskUsage;
import com.haoran.music.service.model.Node3MediaInventory;
import com.haoran.music.vo.admin.MediaReplicaReconciliationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;






@Service
@RequiredArgsConstructor
public class MediaReplicaReconciliationService {
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    private static final int MAX_HASH_FILES = 3;

    private final MediaAssetMapper mediaAssetMapper;
    private final MediaDerivativeTaskMapper mediaDerivativeTaskMapper;
    private final Node3MediaService node3MediaService;

    @Value("${music.node3.reconcile-root:/sdb1/haoranmusicData/Datas}")
    private String reconcileRoot;

    @Value("${music.node3.reconcile-max-hash-bytes:536870912}")
    private long reconcileMaxHashBytes = 536870912L;









    public MediaReplicaReconciliationVO reconcile(int limit, boolean verifyHash, int maxHashFiles) {
        int safeLimit = safeLimit(limit);
        int safeHashLimit = Math.max(0, Math.min(maxHashFiles, MAX_HASH_FILES));
        String root = normalizeRoot(reconcileRoot);
        if (root == null) {
            throw new IllegalStateException("node3媒体对账根目录配置不合法");
        }

        List<MediaAsset> queried = mediaAssetMapper.selectExpectedNode3Assets(root + "/", safeLimit + 1);
        List<MediaAsset> expected = queried == null ? new ArrayList<>() : new ArrayList<>(queried);
        boolean databaseComplete = expected.size() <= safeLimit;
        if (!databaseComplete) {
            expected = new ArrayList<>(expected.subList(0, safeLimit));
        }
        Node3MediaInventory inventory = node3MediaService.inventory(root, safeLimit);

        MediaReplicaReconciliationVO result = new MediaReplicaReconciliationVO();
        result.setCheckedAt(LocalDateTime.now());
        result.setDatabaseComplete(databaseComplete);
        result.setNodeInventoryAvailable(inventory != null && inventory.isAvailable());
        result.setNodeInventoryComplete(inventory != null && inventory.isAvailable() && inventory.isComplete());
        result.setMissingConclusive(databaseComplete && result.isNodeInventoryComplete());
        result.setExtraConclusive(databaseComplete && result.isNodeInventoryComplete());
        result.setExpectedCount(expected.size());
        result.setActualCount(inventory == null || inventory.getFiles() == null ? 0 : inventory.getFiles().size());
        result.setDiskUsage(toDiskUsage(node3MediaService.diskUsage(root)));
        result.setQueueObservation(queueObservation());

        if (!databaseComplete) {
            result.getWarnings().add("数据库资产超过本次上限，missing/extra 不作最终结论");
        }
        if (inventory == null || !inventory.isAvailable()) {
            result.getWarnings().add("node3盘点不可用：" + safeCategory(
                    inventory == null ? "NO_INVENTORY" : inventory.getErrorCategory()));
            return result;
        }
        if (!inventory.isComplete()) {
            result.getWarnings().add("node3文件超过本次上限，missing/extra 不作最终结论");
        }

        Map<String, MediaAsset> expectedByPath = indexExpected(expected, root, result);
        Map<String, Node3MediaInventory.FileEntry> actualByPath = indexActual(inventory.getFiles(), root);
        compare(expectedByPath, actualByPath, root, verifyHash, safeHashLimit, result);
        return result;
    }




    private Map<String, MediaAsset> indexExpected(List<MediaAsset> assets, String root,
                                                   MediaReplicaReconciliationVO result) {
        Map<String, MediaAsset> indexed = new LinkedHashMap<>();
        for (MediaAsset asset : assets) {
            if (asset == null || !isWithinRoot(asset.getStoragePath(), root)) {
                result.getWarnings().add("存在空路径或根目录外数据库资产，assetId="
                        + (asset == null ? "null" : asset.getId()));
                continue;
            }
            MediaAsset previous = indexed.putIfAbsent(asset.getStoragePath(), asset);
            if (previous != null) {
                result.getDuplicateDatabasePaths().add(mismatch(
                        "DUPLICATE_DATABASE_PATH", asset, relative(asset.getStoragePath(), root),
                        asset.getFileSize(), null, null, null));
            }
        }
        return indexed;
    }




    private Map<String, Node3MediaInventory.FileEntry> indexActual(
            List<Node3MediaInventory.FileEntry> files, String root) {
        Map<String, Node3MediaInventory.FileEntry> indexed = new LinkedHashMap<>();
        if (files == null) {
            return indexed;
        }
        for (Node3MediaInventory.FileEntry file : files) {
            if (file != null && isWithinRoot(file.getPath(), root)) {
                indexed.put(file.getPath(), file);
            }
        }
        return indexed;
    }




    private void compare(Map<String, MediaAsset> expected,
                         Map<String, Node3MediaInventory.FileEntry> actual,
                         String root,
                         boolean verifyHash,
                         int maxHashFiles,
                         MediaReplicaReconciliationVO result) {
        int matched = 0;
        int hashChecked = 0;
        int hashUnavailable = 0;
        int hashSkipped = 0;
        for (Map.Entry<String, MediaAsset> entry : expected.entrySet()) {
            MediaAsset asset = entry.getValue();
            Node3MediaInventory.FileEntry file = actual.get(entry.getKey());
            if (file == null) {
                if (result.isMissingConclusive()) {
                    result.getMissing().add(mismatch("MISSING", asset, relative(entry.getKey(), root),
                            asset.getFileSize(), null, null, null));
                }
                continue;
            }
            matched++;
            if (asset.getFileSize() != null && asset.getFileSize() >= 0
                    && asset.getFileSize().longValue() != file.getSize()) {
                result.getSizeMismatch().add(mismatch("SIZE_MISMATCH", asset,
                        relative(entry.getKey(), root), asset.getFileSize(), file.getSize(), null, null));
                continue;
            }
            String expectedHash = normalizeHash(asset.getFileHash());
            if (verifyHash && expectedHash != null && hashChecked < maxHashFiles) {
                if (file.getSize() < 0 || file.getSize() > safeMaxHashBytes()) {
                    hashSkipped++;
                    continue;
                }
                String actualHash = normalizeHash(node3MediaService.sha256(entry.getKey()));
                hashChecked++;
                if (actualHash == null) {
                    hashUnavailable++;
                } else if (!expectedHash.equals(actualHash)) {
                    result.getHashMismatch().add(mismatch("HASH_MISMATCH", asset,
                            relative(entry.getKey(), root), asset.getFileSize(), file.getSize(),
                            prefix(expectedHash), prefix(actualHash)));
                }
            }
        }
        if (result.isExtraConclusive()) {
            for (Node3MediaInventory.FileEntry file : actual.values()) {
                if (!expected.containsKey(file.getPath())) {
                    result.getExtra().add(mismatch("EXTRA", null, relative(file.getPath(), root),
                            null, file.getSize(), null, null));
                }
            }
        }
        result.setMatchedCount(matched);
        result.setHashCheckedCount(hashChecked);
        result.setHashUnavailableCount(hashUnavailable);
        result.setHashSkippedCount(hashSkipped);
    }




    private MediaReplicaReconciliationVO.Mismatch mismatch(String type, MediaAsset asset,
                                                            String relativePath, Long expectedSize,
                                                            Long actualSize, String expectedHashPrefix,
                                                            String actualHashPrefix) {
        MediaReplicaReconciliationVO.Mismatch mismatch = new MediaReplicaReconciliationVO.Mismatch();
        mismatch.setType(type);
        mismatch.setAssetId(asset == null ? null : asset.getId());
        mismatch.setRelativePath(relativePath);
        mismatch.setExpectedSize(expectedSize);
        mismatch.setActualSize(actualSize);
        mismatch.setExpectedHashPrefix(expectedHashPrefix);
        mismatch.setActualHashPrefix(actualHashPrefix);
        return mismatch;
    }




    private Map<String, Object> queueObservation() {
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("mediaReclaim", mediaAssetMapper.selectReclaimObservation());
        observation.put("derivativeStatus", mediaDerivativeTaskMapper.selectStatusSummary());
        observation.put("derivativeRecovery", mediaDerivativeTaskMapper.selectRecoveryObservation());
        observation.put("recentDerivativeRisk", mediaDerivativeTaskMapper.selectRecentRiskTasks(20));
        return observation;
    }




    private Map<String, Object> toDiskUsage(Node3DiskUsage usage) {
        if (usage == null) {
            return Collections.singletonMap("available", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", usage.isAvailable());
        result.put("totalBytes", usage.getTotalBytes());
        result.put("usedBytes", usage.getUsedBytes());
        result.put("availableBytes", usage.getAvailableBytes());
        result.put("usedPercent", usage.getUsedPercent());
        result.put("errorCategory", safeCategory(usage.getErrorCategory()));
        return result;
    }




    private String relative(String path, String root) {
        return path.substring(root.length() + 1);
    }




    private boolean isWithinRoot(String path, String root) {
        return ObjectUtils.isNotEmpty(path) && path.startsWith(root + "/") && !path.contains("..");
    }




    private String normalizeRoot(String root) {
        if (ObjectUtils.isEmpty(root)) {
            return null;
        }
        String normalized = root.trim().replaceAll("/+$", "");
        return normalized.matches("/[A-Za-z0-9_./-]+") && !normalized.contains("..") ? normalized : null;
    }




    private String normalizeHash(String hash) {
        if (ObjectUtils.isEmpty(hash)) {
            return null;
        }
        String normalized = hash.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-f0-9]{64}") ? normalized : null;
    }




    private String prefix(String hash) {
        return hash == null ? null : hash.substring(0, 12);
    }




    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }




    private long safeMaxHashBytes() {
        return Math.max(1L, Math.min(reconcileMaxHashBytes, 1024L * 1024L * 1024L));
    }




    private String safeCategory(String category) {
        return ObjectUtils.isEmpty(category) ? null : category.replaceAll("[^A-Z0-9_]", "_");
    }
}
