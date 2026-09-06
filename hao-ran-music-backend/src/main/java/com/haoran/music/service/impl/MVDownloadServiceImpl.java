package com.haoran.music.service.impl;

import com.haoran.music.enums.VideoQuality;

import cn.hutool.core.util.StrUtil;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ExternalUrlGuard;
import com.haoran.music.common.util.ExternalStreamUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.MV;
import com.haoran.music.mapper.MVMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.MVDownloadService;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.RecommendService;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import java.io.InputStream;
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
public class MVDownloadServiceImpl implements MVDownloadService {

    @Resource
    private MVMapper mvMapper;

    @Resource
    private RecommendService recommendService;

    @Resource
    private UserVipService userVipService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ContentAccessService contentAccessService;

    @Override
    public void downloadMV(Long mvId, String quality, Long userId, HttpServletResponse response) {
        requireSupportedQuality(quality);
        if (userId != null) {
            UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "下载MV");
        }

                 
        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isEmpty(mv)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
        }
        contentAccessService.requireMvAccess(mv, userId);

                     
        checkVipPermissionForQuality(userId, quality);

                      
        String downloadUrl = getDownloadUrl(mv, quality);
        if (StrUtil.isBlank(downloadUrl)) {
            throw new BusinessException("该清晰度暂无下载资源");
        }

                
        response.setContentType("video/mp4");
        response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s - %s.mp4\"",
                mv.getArtistNames() != null ? mv.getArtistNames() : "未知歌手", mv.getName()));

                       
        if (userId != null) {
            try {
                recommendService.recordUserAction(userId, "download_mv", mvId, 1);
            } catch (Exception e) {
                log.warn("event=mv_download_recommendation_record_failed mvId={} errorType={}",
                        mvId, e.getClass().getSimpleName());
            }
        }

               
        streamDownload(downloadUrl, response);

        log.info("event=mv_download_completed mvId={} quality={} userId={}", mvId, quality, userId);
    }

    @Override
    public Object getDownloadInfo(Long mvId, String quality, Long userId) {
        requireSupportedQuality(quality);
        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isEmpty(mv)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
        }
        contentAccessService.requireMvAccess(mv, userId);
        checkVipPermissionForQuality(userId, quality);

        Map<String, Object> info = new HashMap<>();
        info.put("mvId", mvId);
        info.put("mvName", mv.getName());
        info.put("artistNames", mv.getArtistNames() != null ? mv.getArtistNames() : "未知歌手");

                    
        Map<String, Long> sizes = new HashMap<>();
        if (mv.getSize360p() != null && mv.getSize360p() > 0) {
            sizes.put("360p", mv.getSize360p());
        }
        if (mv.getSize720p() != null && mv.getSize720p() > 0) {
            sizes.put("720p", mv.getSize720p());
        }
        if (mv.getSize1080p() != null && mv.getSize1080p() > 0) {
            sizes.put("1080p", mv.getSize1080p());
        }
        info.put("sizes", sizes);

                                          
        String controlledUrl = "/api/mv/download/" + mvId + "?quality=" + quality;
        Long size = getFileSize(mv, quality);
        info.put("url", controlledUrl);
        info.put("size", size);
        info.put("quality", quality);

        return info;
    }

       
                      
                         
                           
       
    private void checkVipPermissionForQuality(Long userId, String quality) {
                                
        VideoQuality videoQuality = parseVideoQuality(quality);

                            
        if (videoQuality.isRequireVip()) {
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
            }
                             
            Boolean isVip = userVipService.isVip(userId);
            if (isVip == null || !isVip) {
                throw new BusinessException("该清晰度需要VIP会员才能下载，请开通VIP后重试");
            }
        }
    }
    private VideoQuality parseVideoQuality(String quality) {
                       
        String normalizedQuality = quality;
        if (quality.endsWith("p")) {
            normalizedQuality = quality.replace("p", "");
        }

                     
        int resolution = Integer.parseInt(normalizedQuality);
        for (VideoQuality vq : com.haoran.music.enums.VideoQuality.values()) {
            if (vq.getResolution() == resolution) {
                return vq;
            }
        }
                 
        return VideoQuality.SD;
    }

    private void requireSupportedQuality(String quality) {
        if (!("360".equals(quality) || "360p".equals(quality)
                || "720".equals(quality) || "720p".equals(quality)
                || "1080".equals(quality) || "1080p".equals(quality)
                || "2160".equals(quality) || "2160p".equals(quality))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的清晰度参数");
        }
    }

       
                  
      
                         
                                                         
                   
       
    private String getDownloadUrl(MV mv, String quality) {
                              
        String normalizedQuality = quality;
        if (!quality.endsWith("p")) {
            normalizedQuality = quality + "p";
        }

        switch (normalizedQuality) {
            case "2160p":
                return null;
            case "1080p":
                if (StrUtil.isNotBlank(mv.getUrl1080p())) {
                    return mv.getUrl1080p();
                }
            case "720p":
                if (StrUtil.isNotBlank(mv.getUrl720p())) {
                    return mv.getUrl720p();
                }
            case "360p":
            default:
                return mv.getUrl360p();
        }
    }

       
                  
      
                         
                         
                   
       
    private Long getFileSize(MV mv, String quality) {
                       
        String normalizedQuality = quality;
        if (!quality.endsWith("p")) {
            normalizedQuality = quality + "p";
        }

        switch (normalizedQuality) {
            case "1080p":
                return mv.getSize1080p();
            case "720p":
                return mv.getSize720p();
            case "360p":
            default:
                return mv.getSize360p();
        }
    }

       
           
      
                           
                             
       
    private void streamDownload(String url, HttpServletResponse response) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            if (!ExternalUrlGuard.validate(url).isAllowed()) {
                throw new BusinessException("外部视频地址不安全");
            }
            connection = ExternalStreamUtil.openGetConnection(url);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new BusinessException("下载资源不可用: HTTP " + responseCode);
            }

            inputStream = connection.getInputStream();
            outputStream = response.getOutputStream();
            ExternalStreamUtil.copy(inputStream, outputStream);

        } catch (IOException e) {
            log.error("event=mv_download_stream_failed errorType={}", e.getClass().getSimpleName());
            throw new BusinessException("下载失败，请稍后重试");
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (IOException e) {
                log.warn("event=mv_download_stream_close_failed errorType={}", e.getClass().getSimpleName());
            }
        }
    }
}
