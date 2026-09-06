package com.haoran.music.service;

import com.haoran.music.vo.search.HotSearchVO;
import com.haoran.music.vo.search.SearchSuggestVO;

import java.util.List;





public interface SearchEnhanceService {








    SearchSuggestVO getSuggest(String keyword, Integer limit);







    List<HotSearchVO> getHotSearch(Integer limit);






    void saveSearchHistory(String keyword);






    List<String> getSearchHistory();




    void clearSearchHistory();



    void recordSearchActivity(Long userId, String keyword);




    void clearSearchActivity(Long userId);




    void deleteSearchActivityItem(Long userId, String keyword);
}
