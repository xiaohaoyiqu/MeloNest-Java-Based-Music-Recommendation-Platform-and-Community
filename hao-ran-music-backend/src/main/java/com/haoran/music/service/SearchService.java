package com.haoran.music.service;

import com.haoran.music.vo.search.SearchResultVO;





public interface SearchService {








    SearchResultVO search(String keyword, Long userId);








    SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size);




    default SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size, String field) {
        return searchSongs(keyword, userId, page, size);
    }








    SearchResultVO searchAlbums(String keyword, Long userId, Integer page, Integer size);








    SearchResultVO searchArtists(String keyword, Long userId, Integer page, Integer size);








    SearchResultVO searchPlaylists(String keyword, Long userId, Integer page, Integer size);








    SearchResultVO searchMvs(String keyword, Long userId, Integer page, Integer size);








    SearchResultVO searchUsers(String keyword, Long userId, Integer page, Integer size);







    java.util.List<String> getHotKeywords(Integer limit);








    java.util.List<String> getSearchHistory(Long userId, Integer limit);






    void clearSearchHistory(Long userId);







    void saveSearchHistory(Long userId, String keyword);







    void deleteSearchHistoryItem(Long userId, String keyword);









    java.util.List<String> getPersonalizedHotKeywords(Long userId, Integer limit);










    java.util.List<String> getPersonalizedSuggestions(String keyword, Long userId, Integer limit);
}
