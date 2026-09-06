package com.haoran.music.service.impl;

import com.haoran.music.dto.localMusic.FileInfoDTO;
import com.haoran.music.service.LocalProxyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

   
                      
                         
   
@Slf4j
@Service
public class LocalProxyServiceImpl implements LocalProxyService {

    @Value("${music.local-proxy.base-url:${audio.local-proxy.base-url:http://192.168.153.133:7777}}")
    private String localProxyBaseUrl;


    @Resource
    private RestTemplate restTemplate;

    @Override
    public FileInfoDTO getFileInfo(String filePath) {
        try {
            String url = localProxyBaseUrl + "/api/file-info?path=" + encodePath(filePath);
            log.debug("event=local_proxy_file_info_requested");
            FileInfoDTO fileInfo = restTemplate.getForObject(url, FileInfoDTO.class);
            if (fileInfo != null) {
                log.debug("[LocalProxy] 文件信息获取成功: size={}, duration={}",
                        fileInfo.getSize(), fileInfo.getAudio() != null ? fileInfo.getAudio().getDuration() : null);
            }
            return fileInfo;
        } catch (Exception e) {
            log.warn("event=local_proxy_file_metadata_query_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public Long getFileSize(String filePath) {
        FileInfoDTO fileInfo = getFileInfo(filePath);
        if (fileInfo != null && fileInfo.getSize() != null) {
            return fileInfo.getSize();
        }
        return 0L;
    }

    @Override
    public Integer getAudioDuration(String filePath) {
        FileInfoDTO fileInfo = getFileInfo(filePath);
        if (fileInfo != null && fileInfo.getAudio() != null) {
            return fileInfo.getAudio().getDuration();
        }
        return 0;
    }

       
              
       
    private String encodePath(String path) {
        try {
            return java.net.URLEncoder.encode(path, "UTF-8");
        } catch (Exception e) {
            log.warn("event=local_proxy_path_encoding_failed");
            return path;
        }
    }
}
