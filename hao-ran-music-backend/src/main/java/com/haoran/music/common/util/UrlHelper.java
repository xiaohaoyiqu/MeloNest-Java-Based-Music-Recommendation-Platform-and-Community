package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

   
           
                            
  
                      
   
@Slf4j
public final class UrlHelper {

    private UrlHelper() {
                    
    }

       
                    
                                                                
      
                            
                                      
       
    public static String toRelativePath(String url) {
        if (ObjectUtils.isEmpty(url)) {
            return null;
        }

                            
        if ("NULL".equals(url)) {
            return null;
        }

                                
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                                        
                int protocolEnd = url.indexOf("://");
                if (protocolEnd == -1) {
                    return url;
                }

                int pathStart = url.indexOf('/', protocolEnd + 3);
                if (pathStart == -1) {
                    return "/";
                }

                String path = url.substring(pathStart);
                log.debug("event=url_relative_path_conversion_succeeded");
                return path;
            } catch (Exception e) {
                log.warn("event=url_relative_path_conversion_failed errorType={}", e.getClass().getSimpleName());
                return url;
            }
        }

                         
        return url;
    }

       
                    
                                                                
      
                               
                                                      
                    
       
    public static String toFullUrl(String relativePath, String baseUrl) {
        if (ObjectUtils.isEmpty(relativePath)) {
            return null;
        }

                          
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }

                             
        if (ObjectUtils.isEmpty(baseUrl)) {
            return relativePath;
        }

                         
        String prefix = baseUrl.endsWith("/") ?
                baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

                              
        String path = relativePath.startsWith("/") ?
                relativePath.substring(1) : relativePath;

        return prefix + "/" + path;
    }

       
               
                             
      
                                        
                   
       
    public static String buildRelativeCoverUrl(String coverUrl) {
        return toRelativePath(coverUrl);
    }

       
               
                           
      
                                        
                   
       
    public static String buildRelativeAudioUrl(String audioUrl) {
        return toRelativePath(audioUrl);
    }

       
             
                                  
      
                        
                       
       
    public static String normalizeUrl(String url) {
        if (ObjectUtils.isEmpty(url)) {
            return null;
        }
        String trimmed = url.trim();
        return "NULL".equals(trimmed) ? null : trimmed;
    }

       
                    
                       
      
                       
                      
       
    public static String encodePath(String url) {
        if (ObjectUtils.isEmpty(url)) {
            return url;
        }

        try {
            int protocolEnd = url.indexOf("://");
            if (protocolEnd == -1) {
                return url;
            }

                        
            String protocol = url.substring(0, protocolEnd + 3);
            String remaining = url.substring(protocolEnd + 3);

                                 
            int hostPathSep = remaining.indexOf('/');
            String hostPort;
            String path = "";

            if (hostPathSep == -1) {
                hostPort = remaining;
            } else {
                hostPort = remaining.substring(0, hostPathSep);
                path = remaining.substring(hostPathSep);
            }

                   
            String encodedPath = encodePathSegments(path);

            String result = protocol + hostPort + encodedPath;

                      
            result = result.replace("://", ":/").replace(":/", "://");

                                     
            result = result.replace("+", "%20");

            log.debug("event=url_path_encoding_succeeded");
            return result;
        } catch (Exception e) {
            log.error("event=url_path_encoding_failed errorType={}", e.getClass().getSimpleName());
            return url;
        }
    }

       
                    
      
                     
                     
       
    private static String encodePathSegments(String path) {
        if (ObjectUtils.isEmpty(path) || path.length() == 0) {
            return path;
        }

                    
        String[] segments = path.split("/", -1);
        StringBuilder pathBuilder = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.length() > 0) {
                try {
                                                                                  
                                                                                             
                    String decoded = URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8.name());
                    String encoded = URLEncoder.encode(decoded, StandardCharsets.UTF_8.name());
                    if (pathBuilder.length() > 0 && pathBuilder.charAt(pathBuilder.length() - 1) != '/') {
                        pathBuilder.append("/");
                    }
                    pathBuilder.append(encoded);
                } catch (Exception e) {
                    if (pathBuilder.length() > 0 && pathBuilder.charAt(pathBuilder.length() - 1) != '/') {
                        pathBuilder.append("/");
                    }
                    pathBuilder.append(segment);
                }
            } else if (i == 0 || i < segments.length - 1) {
                pathBuilder.append("/");
            }
        }

        return pathBuilder.toString();
    }

       
            
      
                                
                      
       
    public static String decodeUrl(String encodedUrl) {
        if (ObjectUtils.isEmpty(encodedUrl)) {
            return encodedUrl;
        }

        try {
            return URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.error("event=url_decode_failed errorType={}", e.getClass().getSimpleName());
            return encodedUrl;
        }
    }

       
                   
                                                   
      
                        
                                     
       
    public static String getExtension(String url) {
        if (ObjectUtils.isEmpty(url)) {
            return "";
        }

                 
        String path = url.split("\\?")[0];

                  
        int lastDotIndex = path.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == path.length() - 1) {
            return "";
        }

                   
        int lastSlashIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));

                   
        if (lastDotIndex > lastSlashIndex) {
            return path.substring(lastDotIndex + 1);
        }

        return "";
    }

       
                    
      
                        
                                  
       
    public static boolean isAudioUrl(String url) {
        if (ObjectUtils.isEmpty(url)) {
            return false;
        }
        String ext = getExtension(url).toLowerCase();
        return "mp3".equals(ext) || "wav".equals(ext) ||
                "flac".equals(ext) || "m4a".equals(ext) ||
                "aac".equals(ext) || "ogg".equals(ext);
    }

       
                    
      
                        
                                  
       
    public static boolean isVideoUrl(String url) {
        if (ObjectUtils.isEmpty(url)) {
            return false;
        }
        String ext = getExtension(url).toLowerCase();
        return "mp4".equals(ext) || "webm".equals(ext) ||
                "mkv".equals(ext) || "avi".equals(ext) ||
                "mov".equals(ext) || "flv".equals(ext);
    }

       
                    
      
                        
                                  
       
    public static boolean isImageUrl(String url) {
        if (ObjectUtils.isEmpty(url)) {
            return false;
        }
        String ext = getExtension(url).toLowerCase();
        return "jpg".equals(ext) || "jpeg".equals(ext) ||
                "png".equals(ext) || "gif".equals(ext) ||
                "webp".equals(ext) || "bmp".equals(ext) ||
                "svg".equals(ext);
    }

       
              
                                                                   
      
                        
                     
       
    public static String mergePaths(String... paths) {
        if (paths == null || paths.length == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            if (ObjectUtils.isEmpty(path)) {
                continue;
            }

                               
            if (i > 0 && path.startsWith("/")) {
                path = path.substring(1);
            }

                      
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            result.append(path);

                                
            if (i < paths.length - 1 && !path.isEmpty()) {
                result.append("/");
            }
        }

        return result.toString();
    }

       
                
                                                     
      
                        
                             
       
    public static String getHost(String url) {
        if (ObjectUtils.isEmpty(url)) {
            return null;
        }

        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (Exception e) {
            log.error("event=url_host_parse_failed errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

       
               
      
                                 
                                            
                  
       
    public static int getPort(String url, int defaultPort) {
        if (ObjectUtils.isEmpty(url)) {
            return defaultPort;
        }

        try {
            URL urlObj = new URL(url);
            int port = urlObj.getPort();
            return port == -1 ? defaultPort : port;
        } catch (Exception e) {
            log.error("event=url_port_parse_failed errorType={}", e.getClass().getSimpleName());
            return defaultPort;
        }
    }
}
