package com.haoran.music.service;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.RankingSnapshot;
import com.haoran.music.entity.RankingSnapshotItem;
import com.haoran.music.mapper.RankingSnapshotItemMapper;
import com.haoran.music.mapper.RankingSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;








@Service
@RequiredArgsConstructor
public class RankingSnapshotBuildService {
    private static final String HOT_SONG_7D = "hot_song_7d";
    private static final String ALL = "all";
    private static final String RULE_VERSION = "qualified-play-count-v1";
    private static final int MAX_ITEMS = 100;

    private final RankingSnapshotMapper rankingSnapshotMapper;
    private final RankingSnapshotItemMapper rankingSnapshotItemMapper;







    @Transactional(rollbackFor = Exception.class)
    public String buildHotSongSnapshot(LocalDateTime windowEnd) {
        LocalDateTime safeWindowEnd = ObjectUtils.isEmpty(windowEnd) ? LocalDateTime.now() : windowEnd;
        LocalDateTime windowStart = safeWindowEnd.minusDays(7);
        if (rankingSnapshotMapper.lockBuild(HOT_SONG_7D, ALL) == null) {
            throw new BusinessException("排行榜构建锁未初始化");
        }
        RankingSnapshot activeSnapshot = rankingSnapshotMapper.selectActiveForUpdate(HOT_SONG_7D, ALL);
        if (activeSnapshot != null && activeSnapshot.getWindowEnd() != null
                && !activeSnapshot.getWindowEnd().isBefore(safeWindowEnd)) {
            return activeSnapshot.getSnapshotId();
        }
        String snapshotId = UUID.randomUUID().toString();

        RankingSnapshot snapshot = new RankingSnapshot();
        snapshot.setSnapshotId(snapshotId);
        snapshot.setRankingType(HOT_SONG_7D);
        snapshot.setPartitionKey(ALL);
        snapshot.setRuleVersion(RULE_VERSION);
        snapshot.setWindowStart(windowStart);
        snapshot.setWindowEnd(safeWindowEnd);
        snapshot.setStatus("building");
        snapshot.setIsActive(false);
        snapshot.setSourceFactCount(0L);
        snapshot.setItemCount(0);
        snapshot.setCreateTime(LocalDateTime.now());
        rankingSnapshotMapper.insert(snapshot);

        List<RankingSnapshotItem> candidates = rankingSnapshotItemMapper.selectHotSongCandidates(
                windowStart, safeWindowEnd, MAX_ITEMS);
        List<RankingSnapshotItem> safeCandidates = candidates == null ? Collections.emptyList() : candidates;
        int rank = 1;
        for (RankingSnapshotItem item : safeCandidates) {
            item.setSnapshotId(snapshotId);
            item.setRankPosition(rank++);
            item.setItemType("song");
            item.setCreateTime(LocalDateTime.now());
            rankingSnapshotItemMapper.insert(item);
        }

        long sourceFactCount = rankingSnapshotItemMapper.countHotSongFacts(windowStart, safeWindowEnd);
        String checksum = checksum(safeCandidates);
        rankingSnapshotMapper.retireActive(HOT_SONG_7D, ALL);
        if (rankingSnapshotMapper.publish(snapshotId, sourceFactCount, safeCandidates.size(), checksum) != 1) {
            throw new BusinessException("排行榜快照发布状态冲突");
        }
        return snapshotId;
    }







    private String checksum(List<RankingSnapshotItem> items) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RankingSnapshotItem item : items) {
                String line = item.getRankPosition() + "|" + item.getItemType() + "|"
                        + item.getItemId() + "|" + item.getScore() + "|"
                        + item.getValidFactCount() + "|" + item.getTieBreaker() + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
