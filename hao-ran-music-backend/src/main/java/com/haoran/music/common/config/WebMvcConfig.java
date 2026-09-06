package com.haoran.music.common.config;

import com.haoran.music.common.interceptor.AuthInterceptor;
import com.haoran.music.common.interceptor.UserActivityInterceptor;
import com.haoran.music.common.interceptor.ApiAccessInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

   
                      
                         
   
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final UserActivityInterceptor userActivityInterceptor;
    private final ApiAccessInterceptor apiAccessInterceptor;

                           
    @Value("${music.upload.avatar-path}")
    private String avatarPath;

    @Value("${music.upload.playlist-cover-path}")
    private String playlistCoverPath;

    public WebMvcConfig(AuthInterceptor authInterceptor, UserActivityInterceptor userActivityInterceptor, ApiAccessInterceptor apiAccessInterceptor) {
        this.authInterceptor = authInterceptor;
        this.userActivityInterceptor = userActivityInterceptor;
        this.apiAccessInterceptor = apiAccessInterceptor;
    }

       
               
                                          
                                                    
       
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
                    
        String avatarLocation = avatarPath.endsWith("/") ? avatarPath : avatarPath + "/";
        String playlistCoverLocation = playlistCoverPath.endsWith("/") ? playlistCoverPath : playlistCoverPath + "/";

                     
        registry.addResourceHandler("/api/files/avatar/**")
                .addResourceLocations("file:" + avatarLocation);

                       
        registry.addResourceHandler("/api/files/playlist-cover/**")
                .addResourceLocations("file:" + playlistCoverLocation);

        log.info("event=static_resource_mapping_registered resource=avatar");
        log.info("event=static_resource_mapping_registered resource=playlist_cover");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
                             
        registry.addInterceptor(apiAccessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/doc.html",
                        "/api/swagger/**",
                        "/api/webjars/**",
                        "/api/v3/api-docs/**",
                        "/api/favicon.ico",
                        "/api/actuator/**",
                        "/api/health",
                        "/api/error"
                );

                                                      
                              
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                                       
                        "/api/auth/**",

                                       
                        "/api/system/health",
                        "/api/actuator/**",
                        "/api/doc.html",
                        "/api/swagger-ui/**",
                        "/api/v3/api-docs/**",
                        "/api/swagger-resources/**",
                        "/api/webjars/**",
                        "/api/favicon.ico",
                        "/api/error",

                                       
                        "/api/files/**",

                                           
                        "/api/song/public/**",
                        "/api/song/page",
                        "/api/song/new",
                        "/api/song/hot",
                        "/api/song/stream/**",
                        "/api/song/url/**",
                        "/api/song/info/**",

                                           
                        "/api/artist/public/**",
                        "/api/artist/page",
                        "/api/artist/list",
                        "/api/artist/hot",
                        "/api/artist/new",
                        "/api/artist/letter/**",
                        "/api/artist/search",
                        "/api/artist/letters",
                        "/api/artist/info/**",

                                           
                        "/api/album/public/**",
                        "/api/album/page",
                        "/api/album/list",
                        "/api/album/hot",
                        "/api/album/info/**",
                        "/api/album/new",

                                           
                                                                                   
                        "/api/playlist/new",

                                           
                        "/api/mv/hot",
                        "/api/mv/newest",
                        "/api/mv/page",
                        "/api/mv/info/**",

                                          
                        "/api/ranking/**",

                                           
                        "/api/recommend/new",
                        "/api/recommend/hot",
                        "/api/recommend/daily",
                        "/api/recommend/discover",
                        "/api/recommend/personal",
                        "/api/recommend/public/**",

                                               
                        "/api/external-content/list/*",
                        "/api/external-content/recommend",

                                           
                        "/api/comment/page",
                        "/api/comment/hot",
                        "/api/comment/*/replies",
                        "/api/comment/info/*",

                                                           
                        "/api/emoji/render",

                                             
                        "/api/user/info/*",

                                         
                        "/api/local-music/download-proxy"
                );

                                            
        registry.addInterceptor(userActivityInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/system/health",
                        "/files/**",
                        "/api/files/**",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/error"
                );
    }
}
