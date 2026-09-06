package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.SearchHistory;

import java.util.List;





public interface SearchHistoryService extends IService<SearchHistory> {










    Boolean addSearchHistory(Long userId, String keyword, Integer searchType, Integer resultCount);







    Boolean clearSearchHistory(Long userId);








    Boolean deleteSearchHistory(Long userId, Long id);








    IPage<SearchHistory> getSearchHistory(Long userId, PageQuery pageQuery);







    List<String> getHotKeywords(Integer limit);









    List<String> getSearchSuggestions(Long userId, String keyword, Integer limit);








    List<SearchHistory> getRecentSearch(Long userId, Integer limit);
}
