package com.haoran.music.service.impl;

import com.haoran.music.enums.SoundQuality;

import cn.hutool.core.util.StrUtil;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ExternalUrlGuard;
import com.haoran.music.common.util.ExternalStreamUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.RecommendService;
import com.haoran.music.service.SongDownloadService;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;





@Slf4j
@Service
public class SongDownloadServiceImpl implements SongDownloadService {

    @Resource
    private SongMapper songMapper;

    @Resource
    private RecommendService recommendService;

    @Resource
    private UserVipService userVipService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ContentAccessService contentAccessService;

    @Override
    public void downloadSong(Long songId, String quality, Long userId, HttpServletResponse response) {
        requireSupportedQuality(quality);
        if (userId != null) {
            UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "下载歌曲");
        }


        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        contentAccessService.requireSongAccess(song, userId);


        checkVipPermissionForQuality(userId, quality);


        String downloadUrl = getDownloadUrl(song, quality);
        if (StrUtil.isBlank(downloadUrl)) {
            throw new BusinessException("该音质暂无下载资源");
        }


        Long fileSize = getFileSize(song, quality);
        if (fileSize == null || fileSize == 0) {
            fileSize = 0L;
        }


        response.setContentType("audio/mpeg");
        response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s - %s.mp3\"",
                song.getArtistNames(), song.getName()));
        if (fileSize > 0) {
            response.setContentLengthLong(fileSize);
        }


        if (userId != null) {
            try {
                recommendService.recordUserAction(userId, "download", songId, 1);
            } catch (Exception e) {
                log.warn("event=song_download_recommendation_record_failed songId={} errorType={}",
                        songId, e.getClass().getSimpleName());
            }
        }


        streamDownload(downloadUrl, response);


        if (canContributeDownloadStats(userId, song)) {
            try {
                songMapper.updateDownloadCount(songId);
                log.info("event=song_download_count_updated songId={}", songId);
            } catch (Exception e) {
                log.warn("event=song_download_count_update_failed songId={} errorType={}",
                        songId, e.getClass().getSimpleName());
            }
        }
    }

    @Override
    public Object getDownloadInfo(Long songId, String quality, Long userId) {
        requireSupportedQuality(quality);
        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        contentAccessService.requireSongAccess(song, userId);
        checkVipPermissionForQuality(userId, quality);

        Map<String, Object> info = new HashMap<>();
        info.put("songId", songId);
        info.put("songName", song.getName());
        info.put("artistNames", song.getArtistNames());


        Map<String, Long> sizes = new HashMap<>();
        if (song.getSizeStandard() != null && song.getSizeStandard() > 0) {
            sizes.put("standard", song.getSizeStandard());
        }
        if (song.getSizeHigh() != null && song.getSizeHigh() > 0) {
            sizes.put("high", song.getSizeHigh());
        }
        if (song.getSizeLossless() != null && song.getSizeLossless() > 0) {
            sizes.put("lossless", song.getSizeLossless());
        }
        info.put("sizes", sizes);


        String controlledUrl = "/api/song/download/" + songId + "?quality=" + quality;
        Long size = getFileSize(song, quality);
        info.put("url", controlledUrl);
        info.put("size", size);
        info.put("quality", quality);

        return info;
    }






    private void checkVipPermissionForQuality(Long userId, String quality) {

        SoundQuality soundQuality = SoundQuality.fromCode(quality);


        if (soundQuality.isRequireVip()) {
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
            }

            Boolean isVip = userVipService.isVip(userId);
            if (isVip == null || !isVip) {
                throw new BusinessException("该音质需要VIP会员才能下载，请开通VIP后重试");
            }
        }
    }

    private void requireSupportedQuality(String quality) {
        if (!"standard".equals(quality) && !"high".equals(quality) && !"lossless".equals(quality)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的音质参数");
        }
    }

    private boolean canContributeDownloadStats(Long userId, Song song) {
        if (userId == null || song == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canContributePublicStats(user)) {
            return false;
        }
        return song.getUploaderId() == null
                || UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById);
    }

    private String getDownloadUrl(Song song, String quality) {
        switch (quality) {
            case "high":
                return song.getUrlHigh();
            case "lossless":
                return song.getUrlLossless();
            default:
                return song.getUrlStandard();
        }
    }








    private Long getFileSize(Song song, String quality) {
        switch (quality) {
            case "high":
                return song.getSizeHigh();
            case "lossless":
                return song.getSizeLossless();
            default:
                return song.getSizeStandard();
        }
    }







    private void streamDownload(String fileUrl, HttpServletResponse response) {
        HttpURLConnection connection = null;
        java.io.InputStream in = null;
        OutputStream out = null;
        try {
            if (!ExternalUrlGuard.validate(fileUrl).isAllowed()) {
                throw new BusinessException("外部音频地址不安全");
            }
            connection = ExternalStreamUtil.openGetConnection(fileUrl);
            ExternalStreamUtil.applyBrowserHeaders(connection, true);

            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 206) {
                throw new BusinessException("获取音频资源失败: HTTP " + responseCode);
            }


            int contentLength = connection.getContentLength();
            if (contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }

            in = connection.getInputStream();
            out = response.getOutputStream();
            ExternalStreamUtil.copy(in, out);
            log.info("event=song_download_stream_completed");

        } catch (IOException e) {
            log.error("event=song_download_stream_failed errorType={}", e.getClass().getSimpleName());
            throw new BusinessException("下载失败，请稍后重试");
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (connection != null) connection.disconnect();
            } catch (IOException e) {
                log.warn("event=song_download_stream_close_failed errorType={}", e.getClass().getSimpleName());
            }
        }
    }
}
