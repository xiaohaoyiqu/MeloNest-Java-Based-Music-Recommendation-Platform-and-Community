package com.haoran.music.common.util;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

   
                      
                                                                                       
   
@Component
public class SshUtil {

    private static final Logger logger = LoggerFactory.getLogger(SshUtil.class);
    private static final int MAX_REMOTE_LYRIC_BYTES = 1024 * 1024;

    @Value("${music.node3.host}")
    private String host;

    @Value("${music.node3.user}")
    private String username;

    @Value("${music.node3.port}")
    private int port;

    @Value("${music.node3.key-path}")
    private String keyPath;

    @Value("${music.node3.lyric-path}")
    private String lyricPath;

    @Value("${music.node3.lyric-url:http://192.168.153.133:8081/lyrics/}")
    private String lyricHttpBaseUrl;

       
                                                  
                           
                                                 
  
    private Session createSession() throws JSchException {
        JSch jsch = new JSch();
        jsch.addIdentity(keyPath);
        logger.info("[SSH] Using key authentication: {}", keyPath);
        Session session = jsch.getSession(username, host, port);
        session.setConfig("StrictHostKeyChecking", "no");
        return session;
    }

       
                               
                                                         
                           
       
    public String readRemoteFile(String filePath) {
        if (!LyricFileResolver.isSafeRelativePath(filePath)) {
            logger.warn("event=remote_lyric_path_rejected");
            return null;
        }
        if (lyricHttpBaseUrl != null && !lyricHttpBaseUrl.trim().isEmpty()) {
            return readRemoteFileOverHttp(filePath);
        }
        return readRemoteFileOverSftp(filePath);
    }

    private String readRemoteFileOverHttp(String filePath) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(normalizeLyricHttpBaseUrl() + encodeRelativePath(filePath));
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                logger.error("event=remote_lyric_http_config_rejected reason=unsupported_scheme");
                return null;
            }

            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/plain, application/octet-stream;q=0.8");
            connection.setRequestProperty("User-Agent", "HaoRanMusicBackend/1.0");

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                logger.warn("event=remote_lyric_http_read_rejected status={}", status);
                return null;
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAX_REMOTE_LYRIC_BYTES) {
                logger.warn("event=remote_lyric_http_read_rejected reason=content_too_large");
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return readContent(inputStream);
            }
        } catch (Exception e) {
            logger.error("event=remote_lyric_http_read_failed errorType={}", e.getClass().getSimpleName());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readRemoteFileOverSftp(String filePath) {
        Session session = null;
        ChannelSftp channelSftp = null;
        InputStream inputStream = null;

        try {
            session = createSession();
            session.connect(30000);
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(30000);

            String fullPath = normalizeLyricRoot() + filePath;
            logger.info("[SSH] Reading file: {}", fullPath);

            inputStream = channelSftp.get(fullPath);
            String content = readContent(inputStream);

            logger.info("[SSH] File read success, length: {} chars", content.length());
            return content;

        } catch (JSchException e) {
            logger.error("[SSH] SSH connection failed: {}", e.getMessage());
            return null;
        } catch (SftpException e) {
            logger.error("[SSH] SFTP operation failed: {}", e.getMessage());
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                logger.info("[SSH] File not found: {}", filePath);
            }
            return null;
        } catch (Exception e) {
            logger.error("[SSH] Read file failed: {}", e.getMessage(), e);
            return null;
        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (Exception e) {
                    logger.warn("[SSH] Failed to close input stream: {}", e.getMessage());
                }
            }
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

       
                                                                     
                                                                             
                                                                                
       
    public Map<String, String> readRemoteLyricFiles(Collection<String> filePaths) {
        Map<String, String> result = new LinkedHashMap<>();
        if (filePaths == null || filePaths.isEmpty()) {
            return result;
        }

        if (lyricHttpBaseUrl != null && !lyricHttpBaseUrl.trim().isEmpty()) {
            for (String filePath : filePaths) {
                String content = readRemoteFile(filePath);
                if (content != null && !content.trim().isEmpty()) {
                    result.put(filePath, content);
                }
            }
            return result;
        }

        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            session = createSession();
            session.connect(30000);
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(30000);

            for (String filePath : filePaths) {
                if (!LyricFileResolver.isSafeRelativePath(filePath)) {
                    logger.warn("[SSH] Refuse unsafe lyric relative path: {}", filePath);
                    continue;
                }
                try (InputStream inputStream = channelSftp.get(normalizeLyricRoot() + filePath)) {
                    String content = readContent(inputStream);
                    if (!content.trim().isEmpty()) {
                        result.put(filePath, content);
                    }
                } catch (SftpException e) {
                    if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                        logger.warn("[SSH] Batch lyric read failed: path={}, error={}",
                                filePath, e.getMessage());
                    }
                } catch (IOException e) {
                    logger.warn("[SSH] Batch lyric content rejected: path={}, error={}",
                            filePath, e.getMessage());
                }
            }
        } catch (JSchException e) {
            logger.error("[SSH] Batch lyric SSH connection failed: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("[SSH] Batch lyric read failed: {}", e.getMessage(), e);
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        return result;
    }

       
                                  
                                                         
                             
       
    public boolean checkRemoteFileExists(String filePath) {
        if (!LyricFileResolver.isSafeRelativePath(filePath)) {
            logger.warn("[SSH] Refuse unsafe lyric relative path: {}", filePath);
            return false;
        }
        if (lyricHttpBaseUrl != null && !lyricHttpBaseUrl.trim().isEmpty()) {
            return readRemoteFile(filePath) != null;
        }
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            session = createSession();
            session.connect(10000);
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(10000);
            channelSftp.stat(normalizeLyricRoot() + filePath);
            return true;

        } catch (JSchException e) {
            if (e.getMessage() != null && e.getMessage().contains("No such file")) {
                return false;
            }
            logger.error("[SSH] Check file failed: {}", e.getMessage());
            return false;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            logger.error("[SSH] Check file failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("[SSH] Check file exception: {}", e.getMessage(), e);
            return false;
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

       
                                     
                            
                                            
  
    public String getLyricFilePath(Long songId) {
        return songId + ".lrc";
    }

       
                                                           
                                                   
                                
                                    
                                                                              
  
    public String getLyricFilePath(String songName, String artistName) {
        return "oranial/" + songName + "-" + artistName + ".lrc";
    }

       
                                                                         
                                             
       
    public String readRemoteLyricFile(String songName, String artistName, Integer lyricType) {
        List<String> candidates = LyricFileResolver.candidatePaths(songName, artistName, lyricType);
        for (String candidate : candidates) {
            String content = readRemoteFile(candidate);
            if (content != null && !content.trim().isEmpty()) {
                logger.info("event=remote_lyric_candidate_matched path={}", candidate);
                return content;
            }
        }
        return null;
    }

    public List<String> getLyricFileCandidates(String songName, String artistName, Integer lyricType) {
        return LyricFileResolver.candidatePaths(songName, artistName, lyricType);
    }

    private String normalizeLyricRoot() {
        if (lyricPath == null || lyricPath.trim().isEmpty()) {
            return "";
        }
        return lyricPath.endsWith("/") ? lyricPath : lyricPath + "/";
    }

    private String normalizeLyricHttpBaseUrl() {
        String baseUrl = lyricHttpBaseUrl.trim();
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private String encodeRelativePath(String filePath) throws IOException {
        String[] segments = filePath.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8.name())
                    .replace("+", "%20"));
        }
        return encoded.toString();
    }

    private String readContent(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_REMOTE_LYRIC_BYTES) {
                throw new IOException("歌词文件超过1MB限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
