



package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.haoran.music.common.util.UrlHelper;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.entity.MV;
import com.haoran.music.entity.Song;
import com.haoran.music.service.MediaPreviewService;
import com.haoran.music.service.Node3MediaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.util.concurrent.ConcurrentHashMap;





@Service
public class MediaPreviewServiceImpl implements MediaPreviewService {

    static final int PREVIEW_SECONDS = 30;

    private final Node3MediaService node3MediaService;
    private final ConcurrentHashMap<String, Object> generationLocks = new ConcurrentHashMap<>();

    @Value("${music.upload.song-path:/sdb1/haoranmusicData/Datas/songs/}")
    private String songBasePath;

    @Value("${music.upload.mv-path:/sdb1/haoranmusicData/Datas/mvs/}")
    private String mvBasePath;

    @Value("${music.nginx-url-prefix:http://192.168.153.131:3223/}")
    private String nginxUrlPrefix;

    public MediaPreviewServiceImpl(Node3MediaService node3MediaService) {
        this.node3MediaService = node3MediaService;
    }

    @Override
    public String ensureSongPreview(Song song, String sourceUrl) {
        if (song == null || song.getId() == null) {
            return null;
        }
        String target = songPreviewPath(song.getId());
        if (node3MediaService.exists(target)) {
            return songPreviewUrl(song.getId());
        }
        String source = sourceRemotePath(sourceUrl, "songs", songBasePath);
        if (source == null || !generate("song:" + song.getId(), source, target, false)) {
            return null;
        }
        return songPreviewUrl(song.getId());
    }

    @Override
    public String findSongPreview(Long songId) {
        return songId != null && node3MediaService.exists(songPreviewPath(songId)) ? songPreviewUrl(songId) : null;
    }

    @Override
    public String ensureMvPreview(MV mv, String sourceUrl) {
        if (mv == null || mv.getId() == null) {
            return null;
        }
        String target = mvPreviewPath(mv.getId());
        if (node3MediaService.exists(target)) {
            return mvPreviewUrl(mv.getId());
        }
        String source = sourceRemotePath(sourceUrl, "mvs", mvBasePath);
        if (source == null || !generate("mv:" + mv.getId(), source, target, true)) {
            return null;
        }
        return mvPreviewUrl(mv.getId());
    }

    @Override
    public String findMvPreview(Long mvId) {
        return mvId != null && node3MediaService.exists(mvPreviewPath(mvId)) ? mvPreviewUrl(mvId) : null;
    }

    private boolean generate(String key, String source, String target, boolean video) {
        Object newLock = new Object();
        Object lock = generationLocks.putIfAbsent(key, newLock);
        Object activeLock = lock == null ? newLock : lock;
        synchronized (activeLock) {
            try {
                if (node3MediaService.exists(target)) {
                    return true;
                }
                return video
                        ? node3MediaService.generateVideoPreview(source, target, PREVIEW_SECONDS)
                        : node3MediaService.generateAudioPreview(source, target, PREVIEW_SECONDS);
            } finally {
                generationLocks.remove(key, activeLock);
            }
        }
    }

    private String sourceRemotePath(String sourceUrl, String mediaRoot, String basePath) {
        if (StrUtil.isBlank(sourceUrl) || StrUtil.isBlank(basePath)) {
            return null;
        }
        try {
            String raw = sourceUrl.trim();
            URI uri = raw.startsWith("/")
                    ? URI.create(UrlHelper.encodePath("http://media.local" + raw))
                    : URI.create(UrlHelper.encodePath(raw));
            String path = uri.getRawPath();
            String prefix = "/" + mediaRoot + "/";
            if (path == null || !path.startsWith(prefix) || path.toLowerCase().contains("%2e")) {
                return null;
            }
            String relative = URLDecoder.decode(path.substring(prefix.length()), "UTF-8").replace('\\', '/');
            if (relative.isEmpty() || relative.startsWith("/") || !WorkProcessingUtil.isPathSafe(relative)) {
                return null;
            }
            return normalizeDirectory(basePath) + relative;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String songPreviewPath(Long songId) {
        return normalizeDirectory(songBasePath) + "preview/" + songId + "_30s.mp3";
    }

    private String mvPreviewPath(Long mvId) {
        return normalizeDirectory(mvBasePath) + "preview/" + mvId + "_30s.mp4";
    }

    private String songPreviewUrl(Long songId) {
        return normalizeDirectory(nginxUrlPrefix) + "songs/preview/" + songId + "_30s.mp3";
    }

    private String mvPreviewUrl(Long mvId) {
        return normalizeDirectory(nginxUrlPrefix) + "mvs/preview/" + mvId + "_30s.mp4";
    }

    private String normalizeDirectory(String value) {
        String normalized = value.trim().replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
