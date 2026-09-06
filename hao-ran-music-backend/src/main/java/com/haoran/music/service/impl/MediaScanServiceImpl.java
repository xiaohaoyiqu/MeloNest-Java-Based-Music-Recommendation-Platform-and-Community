package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.service.CacheService;
import com.haoran.music.common.util.VideoCompressUtil;
import com.haoran.music.entity.MV;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.MVMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.service.MediaScanService;
import com.haoran.music.service.Node3MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

   
                                            
  
                      
   
@Slf4j
@Service
public class MediaScanServiceImpl extends ServiceImpl<SongMapper, Song> implements MediaScanService {

    private static final int MAX_SCAN_LIMIT = 1000;
    private static final int DURATION_TOLERANCE_SECONDS = 1;

    @Resource
    private MVMapper mvMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private Node3MediaService node3MediaService;

    @Resource
    private CacheService cacheService;

    @Value("${music.node3.reconcile-root:/sdb1/haoranmusicData/Datas}")
    private String node3MediaRoot;

    @Value("${music.upload.nginx-url-prefix}")
    private String nginxUrlPrefix;

    @Override
    public int scanAndUpdateMVDuration(int limit) {
        int safeLimit = normalizeLimit(limit);
        LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                .eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                .and(w -> w.isNotNull(MV::getUrl1080p)
                        .or().isNotNull(MV::getUrl720p)
                        .or().isNotNull(MV::getUrl360p)
                        .or().isNotNull(MV::getUrl2160p))
                .orderByAsc(MV::getId)
                .last("LIMIT " + safeLimit);

        int updatedCount = 0;
        for (MV mv : mvMapper.selectList(wrapper)) {
            updatedCount += updateMVDuration(mv);
        }
        log.info("node3 MV时长扫描完成: inspectedLimit={}, updated={}", safeLimit, updatedCount);
        return updatedCount;
    }

    @Override
    public int scanAndUpdateSongDuration(int limit) {
        int safeLimit = normalizeLimit(limit);
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .isNotNull(Song::getUrlStandard)
                .and(w -> w.isNull(Song::getDuration).or().eq(Song::getDuration, 0))
                .orderByAsc(Song::getId)
                .last("LIMIT " + safeLimit);

        int updatedCount = 0;
        for (Song song : songMapper.selectList(wrapper)) {
            updatedCount += updateSongDuration(song);
        }
        log.info("node3 歌曲时长扫描完成: inspectedLimit={}, updated={}", safeLimit, updatedCount);
        return updatedCount;
    }

    @Override
    public int scanSpecificMVs(List<Long> mvIds) {
        if (mvIds == null || mvIds.isEmpty()) {
            return 0;
        }
        int updatedCount = 0;
        List<Long> boundedMvIds = mvIds.stream().filter(id -> id != null).distinct()
                .limit(MAX_SCAN_LIMIT).collect(Collectors.toList());
        for (Long mvId : boundedMvIds) {
            MV mv = mvMapper.selectById(mvId);
            if (mv == null || CommonConstants.DELETED.equals(mv.getDeleted())) {
                log.warn("MV不存在或已删除: mvId={}", mvId);
                continue;
            }
            updatedCount += updateMVDuration(mv);
        }
        log.info("指定MV时长扫描完成: requested={}, updated={}", mvIds.size(), updatedCount);
        return updatedCount;
    }

    @Override
    public int scanSpecificSongs(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return 0;
        }
        int updatedCount = 0;
        List<Long> boundedSongIds = songIds.stream().filter(id -> id != null).distinct()
                .limit(MAX_SCAN_LIMIT).collect(Collectors.toList());
        for (Long songId : boundedSongIds) {
            Song song = songMapper.selectById(songId);
            if (song == null || CommonConstants.DELETED.equals(song.getDeleted())
                    || !CommonConstants.STATUS_NORMAL.equals(song.getStatus())) {
                log.warn("歌曲不存在、已删除或已下架: songId={}", songId);
                continue;
            }
            updatedCount += updateSongDuration(song);
        }
        log.info("指定歌曲时长扫描完成: requested={}, updated={}", songIds.size(), updatedCount);
        return updatedCount;
    }

    private int updateMVDuration(MV mv) {
        String sourceUrl = firstNotBlank(mv.getUrl1080p(), mv.getUrl720p(), mv.getUrl360p(), mv.getUrl2160p());
        String remotePath = getUrlToNode3Path(sourceUrl);
        Integer probedDuration = probeDuration(remotePath);
        if (!needsDurationUpdate(mv.getDuration(), probedDuration)) {
            return 0;
        }

        Integer previousDuration = mv.getDuration();
        mv.setDuration(probedDuration);
        if (mvMapper.updateById(mv) != 1) {
            log.warn("MV时长更新失败: mvId={}", mv.getId());
            return 0;
        }
        cacheService.clearMVCache(mv.getId());
        log.info("更新MV时长: mvId={}, previous={}, current={}", mv.getId(), previousDuration, probedDuration);
        return 1;
    }

    private int updateSongDuration(Song song) {
        String remotePath = getUrlToNode3Path(song.getUrlStandard());
        Integer probedDuration = probeDuration(remotePath);
        if (!needsDurationUpdate(song.getDuration(), probedDuration)) {
            return 0;
        }

        Integer previousDuration = song.getDuration();
        song.setDuration(probedDuration);
        if (songMapper.updateById(song) != 1) {
            log.warn("歌曲时长更新失败: songId={}", song.getId());
            return 0;
        }
        cacheService.clearSongCache(song.getId());
        log.info("更新歌曲时长: songId={}, previous={}, current={}", song.getId(), previousDuration, probedDuration);
        return 1;
    }

    private Integer probeDuration(String remotePath) {
        if (StrUtil.isBlank(remotePath)) {
            return null;
        }
        VideoCompressUtil.VideoInfo videoInfo = node3MediaService.probeVideo(remotePath);
        return videoInfo != null && videoInfo.getDuration() > 0 ? videoInfo.getDuration() : null;
    }

    private boolean needsDurationUpdate(Integer current, Integer probed) {
        return probed != null && (current == null || current <= 0
                || Math.abs(current - probed) > DURATION_TOLERANCE_SECONDS);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_SCAN_LIMIT));
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

       
                                             
       
    private String getUrlToNode3Path(String url) {
        if (StrUtil.isBlank(url) || StrUtil.isBlank(node3MediaRoot)) {
            return null;
        }
        try {
            URI candidateUri = URI.create(url.replace(" ", "%20"));
            String relativePath = candidateUri.getPath();
            if (candidateUri.isAbsolute()) {
                URI baseUri = URI.create(nginxUrlPrefix);
                if (!sameOrigin(baseUri, candidateUri) || candidateUri.getUserInfo() != null) {
                    return null;
                }
                String basePath = baseUri.getPath();
                String candidatePath = candidateUri.getPath();
                if (basePath == null || candidatePath == null || !candidatePath.startsWith(basePath)) {
                    return null;
                }
                relativePath = candidatePath.substring(basePath.length());
            }
            if (StrUtil.isBlank(relativePath) || relativePath.contains("\\") || relativePath.indexOf('\0') >= 0) {
                return null;
            }
            while (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            String normalizedRoot = node3MediaRoot.replace('\\', '/').replaceAll("/+$", "");
            String targetPath = new URI(null, null, normalizedRoot + "/" + relativePath, null)
                    .normalize().getPath();
            if (!targetPath.startsWith(normalizedRoot + "/")) {
                return null;
            }
            return targetPath;
        } catch (Exception exception) {
            log.debug("event=media_scan_url_unmapped");
            return null;
        }
    }

    private boolean sameOrigin(URI expected, URI candidate) {
        return expected.getScheme() != null && expected.getHost() != null
                && expected.getScheme().equalsIgnoreCase(candidate.getScheme())
                && expected.getHost().equalsIgnoreCase(candidate.getHost())
                && effectivePort(expected) == effectivePort(candidate);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
