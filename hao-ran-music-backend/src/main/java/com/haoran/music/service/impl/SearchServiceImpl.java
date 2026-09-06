package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ApplicationContextProvider;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.SearchLimitUtil;
import com.haoran.music.entity.SearchHistory;
import com.haoran.music.service.SearchEnhanceService;
import com.haoran.music.service.SearchHistoryService;
import com.haoran.music.service.SearchService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.UserPortraitService;
import com.haoran.music.service.UserStatisticsService;
import com.haoran.music.service.search.SearchEngineAdapter;
import com.haoran.music.util.SearchPersonalizationUtil;
import com.haoran.music.vo.search.SearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
   
                      
                       
   
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private static final int MAX_SEARCH_KEYWORD_LENGTH = 100;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private SearchHistoryService searchHistoryService;

    @Resource
    private UserStatisticsService userStatisticsService;

    @Resource
    private SearchEnhanceService searchEnhanceService;

    @Resource
    private SearchEngineAdapter searchEngineAdapter;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;
    private static final int HOT_KEYWORDS_LIMIT = 10;
    private static final List<String> DEFAULT_KEYWORDS = Arrays.asList(
            "周杰伦", "林俊杰", "邓紫棋", "陈奕迅",
            "薛之谦", "李荣浩", "毛不易", "华晨宇",
            "流行", "摇滚", "民谣", "古风", "说唱"
    );

    @Override
    public SearchResultVO search(String keyword, Long userId) {
        String normalizedKeyword = validateKeyword(keyword);

        if (ObjectUtils.isNotEmpty(userId)) {
            saveSearchHistory(userId, normalizedKeyword);
        }

        return searchEngineAdapter.search(normalizedKeyword, userId);
    }

    @Override
    public SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size) {
        return searchSongs(keyword, userId, page, size, "all");
    }

    @Override
    public SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size, String field) {
        String normalizedKeyword = validateKeyword(keyword);
        return searchEngineAdapter.searchSongs(normalizedKeyword, userId, page, size, normalizeSongSearchField(field));
    }

    @Override
    public SearchResultVO searchAlbums(String keyword, Long userId, Integer page, Integer size) {
        String normalizedKeyword = validateKeyword(keyword);
        return searchEngineAdapter.searchAlbums(normalizedKeyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchArtists(String keyword, Long userId, Integer page, Integer size) {
        String normalizedKeyword = validateKeyword(keyword);
        return searchEngineAdapter.searchArtists(normalizedKeyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchPlaylists(String keyword, Long userId, Integer page, Integer size) {
        String normalizedKeyword = validateKeyword(keyword);
        return searchEngineAdapter.searchPlaylists(normalizedKeyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchMvs(String keyword, Long userId, Integer page, Integer size) {
        String normalizedKeyword = validateKeyword(keyword);
        return searchEngineAdapter.searchMvs(normalizedKeyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchUsers(String keyword, Long userId, Integer page, Integer size) {
        String normalizedKeyword = validateKeyword(keyword);
        return searchEngineAdapter.searchUsers(normalizedKeyword, userId, page, size);
    }

    private String validateKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Keyword cannot be blank");
        }
        String normalizedKeyword = keyword.trim();
        if (normalizedKeyword.length() > MAX_SEARCH_KEYWORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "搜索关键词不能超过100个字符");
        }
        return normalizedKeyword;
    }

    private String normalizeSongSearchField(String field) {
        String normalizedField = StrUtil.blankToDefault(field, "all").trim().toLowerCase(Locale.ROOT);
        if ("all".equals(normalizedField)
                || "title".equals(normalizedField)
                || "artist".equals(normalizedField)
                || "album".equals(normalizedField)) {
            return normalizedField;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的歌曲搜索范围");
    }
    @Override
    public List<String> getHotKeywords(Integer limit) {
        final int resultSize = SearchLimitUtil.normalize(limit);

                              
        String cacheKey = RedisConstants.SEARCH_PREFIX + musicIntelligenceCacheService.searchVersionSegment()
                + "hot:keywords:" + resultSize;
        return CacheHelper.getOrLoad(
                redisUtils, cacheKey,
                () -> loadHotKeywords(resultSize),
                1, TimeUnit.HOURS, List.class
        );
    }

       
                          
      
                        
                      
       
    private List<String> loadHotKeywords(int limit) {
        try {
                                                
            LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(SearchHistory::getKeyword)
                    .isNotNull(SearchHistory::getKeyword)
                    .ge(SearchHistory::getCreateTime, LocalDateTime.now().minusDays(30))
                    .groupBy(SearchHistory::getKeyword)
                    .last("ORDER BY COUNT(*) DESC LIMIT " + limit);

            List<Map<String, Object>> results = searchHistoryService.getBaseMapper().selectMaps(wrapper);

            List<String> hotKeywords = results.stream()
                    .map(map -> (String) map.get("keyword"))
                    .collect(Collectors.toList());

                                    
            if (!hotKeywords.isEmpty()) {
                log.info("从数据库加载热门搜索关键词: {}", hotKeywords);
                return hotKeywords;
            }

                                    
            log.info("数据库暂无搜索历史，使用默认关键词");
            return DEFAULT_KEYWORDS.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("从数据库加载热门关键词失败，使用默认关键词: {}", e.getClass().getSimpleName());
            return DEFAULT_KEYWORDS.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

       
             
      
                         
                        
                     
       
    @Override
    public List<String> getSearchHistory(Long userId, Integer limit) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }
        int resultSize = SearchLimitUtil.normalize(limit);

        try {
            String key = RedisConstants.SEARCH_PREFIX + "history:" + userId;
            Object cached = redisUtils.get(key);

            if (ObjectUtils.isNotEmpty(cached)) {
                                                 
                List<String> history;
                if (cached instanceof List) {
                    history = new ArrayList<>();
                    for (Object item : (List<?>) cached) {
                        if (item instanceof String) {
                            history.add((String) item);
                        }
                    }
                } else {
                    return new ArrayList<>();
                }

                return history.stream()
                        .limit(resultSize)
                        .collect(Collectors.toList());
            } else {
                                 
                log.info("Redis缓存为空，从数据库加载搜索历史: userId={}", userId);
                List<SearchHistory> dbHistory = searchHistoryService.getRecentSearch(userId, resultSize);

                               
                List<String> keywords = dbHistory.stream()
                        .map(SearchHistory::getKeyword)
                        .distinct()
                        .limit(resultSize)
                        .collect(Collectors.toList());

                            
                if (!keywords.isEmpty()) {
                    redisUtils.set(key, keywords, 7, TimeUnit.DAYS);
                    log.info("从数据库加载了{}条搜索历史，已回写Redis", keywords.size());
                }

                return keywords;
            }
        } catch (Exception e) {
            log.error("获取搜索历史失败: userId={}", userId);
        }

        return new ArrayList<>();
    }

    @Override
    public void clearSearchHistory(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }

                    
        String key = RedisConstants.SEARCH_PREFIX + "history:" + userId;
        redisUtils.delete(key);

                    
        try {
            searchHistoryService.clearSearchHistory(userId);
            log.info("清除用户搜索历史（Redis+数据库）: userId={}", userId);
        } catch (Exception e) {
            log.error("清除数据库搜索历史失败: userId={}", userId);
        }

        clearEnhancedSearchActivity(userId);
    }

       
               
      
                          
                         
       
    @Override
    public void deleteSearchHistoryItem(Long userId, String keyword) {
        if (ObjectUtils.isEmpty(userId) || StrUtil.isBlank(keyword)) {
            return;
        }

        try {
            String key = RedisConstants.SEARCH_PREFIX + "history:" + userId;
            Object cached = redisUtils.get(key);

            if (ObjectUtils.isNotEmpty(cached) && cached instanceof List) {
                List<String> history = new ArrayList<>();
                for (Object item : (List<?>) cached) {
                    if (item instanceof String) {
                        history.add((String) item);
                    }
                }

                          
                history.remove(keyword);

                          
                redisUtils.set(key, history, 7, TimeUnit.DAYS);

                log.info("删除单条搜索历史（Redis）: userId={}, keyword={}", userId, keyword);
            }

                        
            try {
                                   
                LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SearchHistory::getUserId, userId)
                        .eq(SearchHistory::getKeyword, keyword.trim());
                searchHistoryService.remove(wrapper);

                log.info("删除单条搜索历史（数据库）: userId={}, keyword={}", userId, keyword);
            } catch (Exception dbEx) {
                log.warn("删除数据库搜索历史失败: userId={}, keyword={}", userId, keyword);
            }
        } catch (Exception e) {
            log.error("删除单条搜索历史失败: userId={}, keyword={}", userId, keyword);
        }

        deleteEnhancedSearchActivityItem(userId, keyword);
    }

       
             
      
                         
                         
       
    @Override
    public void saveSearchHistory(Long userId, String keyword) {
        if (ObjectUtils.isEmpty(userId) || StrUtil.isBlank(keyword)) {
            return;
        }

        keyword = keyword.trim();

        try {
            String key = RedisConstants.SEARCH_PREFIX + "history:" + userId;
            Object cached = redisUtils.get(key);

            List<String> history;
            if (ObjectUtils.isNotEmpty(cached) && cached instanceof List) {
                         
                history = new ArrayList<>();
                for (Object item : (List<?>) cached) {
                    if (item instanceof String) {
                        history.add((String) item);
                    }
                }
            } else {
                history = new ArrayList<>();
            }

                                    
            history.remove(keyword);
            history.add(0, keyword);

                          
            if (history.size() > 50) {
                history = history.subList(0, 50);
            }

                   
            redisUtils.set(key, history, 7, TimeUnit.DAYS);

                                    
            try {
                                                        
                searchHistoryService.addSearchHistory(userId, keyword.trim(), 0, 0);
            } catch (Exception dbEx) {
                                 
                log.warn("保存搜索历史到数据库失败: userId={}, keyword={}", userId, keyword);
            }
            userStatisticsService.recordSearch(userId);
            recordEnhancedSearchActivity(userId, keyword);

            log.debug("保存搜索历史: userId={}, keyword={}", userId, keyword);
        } catch (Exception e) {
            log.error("保存搜索历史失败: userId={}, keyword={}", userId, keyword);
        }
    }

    private void recordEnhancedSearchActivity(Long userId, String keyword) {
        try {
            searchEnhanceService.recordSearchActivity(userId, keyword);
        } catch (Exception e) {
            log.warn("同步增强搜索记录失败: userId={}, keyword={}", userId, keyword);
        }
    }

    private void clearEnhancedSearchActivity(Long userId) {
        try {
            searchEnhanceService.clearSearchActivity(userId);
        } catch (Exception e) {
            log.warn("清空增强搜索记录失败: userId={}", userId);
        }
    }

    private void deleteEnhancedSearchActivityItem(Long userId, String keyword) {
        try {
            searchEnhanceService.deleteSearchActivityItem(userId, keyword);
        } catch (Exception e) {
            log.warn("删除增强搜索记录失败: userId={}, keyword={}", userId, keyword);
        }
    }
       
                   
      
                         
                        
                         
       
    @Override
    public List<String> getPersonalizedHotKeywords(Long userId, Integer limit) {
        final int resultSize = SearchLimitUtil.normalize(limit);

                       
        if (ObjectUtils.isEmpty(userId)) {
            return DEFAULT_KEYWORDS.stream()
                    .limit(resultSize)
                    .collect(Collectors.toList());
        }

                              
        String cacheKey = RedisConstants.SEARCH_PREFIX + musicIntelligenceCacheService.searchVersionSegment()
                + "hot:keywords:" + userId + ":" + resultSize;
        return CacheHelper.getOrLoad(
                redisUtils, cacheKey,
                () -> loadPersonalizedHotKeywords(userId, resultSize),
                30, TimeUnit.MINUTES, List.class
        );
    }

       
                   
      
                         
                        
                         
       
    private List<String> loadPersonalizedHotKeywords(Long userId, int limit) {
        try {
                       
            UserPortraitService userPortraitService = getBean(UserPortraitService.class);
            Map<String, Object> userPortrait = userPortraitService.getUserPortrait(userId);

                              
            return SearchPersonalizationUtil.getPersonalizedHotKeywords(
                    userPortrait, DEFAULT_KEYWORDS, limit);
        } catch (Exception e) {
            log.warn("获取个性化热门关键词失败，使用默认关键词: userId={}", userId);
            return DEFAULT_KEYWORDS.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

       
                
      
                           
                         
                        
                      
       
    @Override
    public List<String> getPersonalizedSuggestions(String keyword, Long userId, Integer limit) {
        if (StrUtil.isBlank(keyword)) {
            return new ArrayList<>();
        }

        final int resultSize = SearchLimitUtil.normalize(limit);
        List<String> defaultSuggestions = generateDefaultSuggestions(keyword);

                      
        if (ObjectUtils.isEmpty(userId)) {
            return defaultSuggestions.stream()
                    .limit(resultSize)
                    .collect(Collectors.toList());
        }

        try {
                       
            UserPortraitService userPortraitService = getBean(UserPortraitService.class);
            Map<String, Object> userPortrait = userPortraitService.getUserPortrait(userId);

                           
            return SearchPersonalizationUtil.getPersonalizedSuggestions(
                    keyword, userPortrait, defaultSuggestions);
        } catch (Exception e) {
            log.warn("获取个性化搜索建议失败，使用默认建议: keyword={}, userId={}", keyword, userId);
            return defaultSuggestions.stream()
                    .limit(resultSize)
                    .collect(Collectors.toList());
        }
    }

       
               
      
                           
                     
       
    private List<String> generateDefaultSuggestions(String keyword) {
        List<String> suggestions = new ArrayList<>();

                      
        suggestions.add(keyword + " 的歌");
        suggestions.add(keyword + " 专辑");
        suggestions.add(keyword + " 现场");

                  
        suggestions.add("类似 " + keyword);
        suggestions.add(keyword + " 翻唱");

        return suggestions;
    }

       
                    
      
                              
                        
                     
       
    private <T> T getBean(Class<T> beanClass) {
        return ApplicationContextProvider
                .getApplicationContext()
                .getBean(beanClass);
    }
}
